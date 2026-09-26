package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Small quantity subproblem portfolio. A stopped backend is never an infeasibility proof. */
final class CountQuickSolve implements AutoCloseable {

    private final PlanningBudget budget;
    private final List<ExactLinearProgram.Constraint> rows;
    private final int variables;
    private final boolean factor;
    private CountBounds bounds;
    private CountReduction reduction;
    private CountPartition partition;
    private CountComponents components;
    private CountMeetInMiddle matching;
    private CountBoolean binary;
    private ExactLinearProgram linear;
    private CountLatticeRepair repair;
    private ExactRational[] point;
    private BigInteger[] counts;
    private int phase, attempts;
    private boolean complete, infeasible;
    private boolean trial;
    private long memory;
    private final List<CountConflict> learned = new ArrayList<>();

    CountQuickSolve(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper, PlanningBudget budget) {
        this(rows, lower, upper, budget, false);
    }

    CountQuickSolve(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper, PlanningBudget budget, boolean saturate) {
        this(rows, lower, upper, budget, saturate, true);
    }

    CountQuickSolve(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper, PlanningBudget budget, boolean saturate, boolean factor) {
        this.factor = factor;
        this.budget = budget;
        variables = lower.length;
        this.rows = new ArrayList<>(rows);
        long bytes = 1024 + 384L * variables + 16L * rows.size();
        if (!budget.tryReserve(bytes)) {
            complete = true;
            return;
        }
        memory = bytes;
        for (int i = 0; i < variables; i++) {
            this.rows.add(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE.negate()), lower[i].negate()));
            if (upper[i] != null) this.rows.add(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE), upper[i]));
        }
        try {
            if (saturate) {
                BitSet taken = new BitSet();
                for (var row : rows) {
                    budget.check();
                    if (row.terms().size() < 2 || row.terms().size() > 8 || !row.upper().equals(BigInteger.ONE) ||
                            row.terms().entrySet().stream().anyMatch(e -> !e.getValue().equals(BigInteger.ONE) ||
                                    lower[e.getKey()].signum() != 0 || !BigInteger.ONE.equals(upper[e.getKey()]) || taken.get(e.getKey())))
                        continue;
                    Map<Integer, BigInteger> reverse = new LinkedHashMap<>();
                    row.terms().keySet().forEach(id -> {
                        reverse.put(id, BigInteger.ONE.negate());
                        taken.set(id);
                    });
                    this.rows.add(new ExactLinearProgram.Constraint(reverse, BigInteger.ONE.negate()));
                    trial = true;
                }
            }
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    boolean step() {
        if (complete) return true;
        budget.check();
        if (phase == 0) {
            if (bounds == null) bounds = new CountBounds(variables, rows, budget);
            if (!bounds.step()) return false;
            if (bounds.blocked()) return finish(null, true);
            reduction = new CountReduction(rows, bounds.lowerBounds(), bounds.upperBounds(), budget);
            bounds.close();
            bounds = null;
            phase++;
        }
        if (phase == 1) {
            if (!reduction.step()) return false;
            if (factor) {
                if (components == null) components = new CountComponents(reduction.rows(), reduction.lower(), reduction.upper(), budget);
                if (!components.step()) return false;
                var value = components.counts();
                boolean impossible = components.infeasible();
                if (!trial) memory += CountMapping.retain(learned, CountMapping.representatives(reduction.representatives()).conflicts(components.learnedConflicts(), budget), budget);
                components.close();
                components = null;
                if (value != null || impossible) return finish(value, impossible);
            }
            partition = new CountPartition(reduction.rows(), reduction.lower(), reduction.upper(), budget);
            phase++;
        }
        if (phase == 2) {
            if (!partition.step()) return false;
            var value = partition.counts();
            partition.close();
            partition = null;
            if (value != null) return finish(value, false);
            boolean weighted = reduction.rows().stream().flatMap(row -> row.terms().values().stream()).anyMatch(v -> v.abs().compareTo(BigInteger.ONE) > 0);
            if (weighted) matching = new CountMeetInMiddle(reduction.rows(), reduction.lower(), reduction.upper(), budget);
            phase++;
        }
        if (phase == 3) {
            if (matching != null) {
                if (!matching.step()) return false;
                var value = matching.counts();
                boolean impossible = matching.infeasible();
                matching.close();
                matching = null;
                if (value != null || impossible) return finish(value, impossible);
            }
            binary = new CountBoolean(reduction.rows(), reduction.lower(), reduction.upper(), budget);
            phase++;
        }
        if (phase == 4) {
            if (!binary.step()) return false;
            var value = binary.counts();
            boolean impossible = binary.infeasible();
            if (!trial) memory += CountMapping.retain(learned, CountMapping.representatives(reduction.representatives()).conflicts(binary.learnedConflicts(), budget), budget);
            binary.close();
            binary = null;
            if (value != null || impossible) return finish(value, impossible);
            if (reduction.variables() > 48 || reduction.rows().size() > 192) return finish(null, false);
            BigInteger[] objective = new BigInteger[reduction.variables()];
            Arrays.fill(objective, BigInteger.ONE.negate());
            linear = new ExactLinearProgram(objective.length, reduction.rows(), objective, budget);
            phase++;
        }
        if (phase == 5) {
            if (!linear.step()) return false;
            var status = linear.result();
            point = linear.point();
            linear.close();
            linear = null;
            if (status != ExactLinearProgram.Result.OPTIMAL) return finish(null, status == ExactLinearProgram.Result.INFEASIBLE);
            if (Arrays.stream(point).allMatch(ExactRational::integral))
                return finish(Arrays.stream(point).map(ExactRational::numerator).toArray(BigInteger[]::new), false);
            phase++;
        }
        if (repair == null) repair = new CountLatticeRepair(reduction.rows(), reduction.lower(), reduction.upper(), point, attempts, budget);
        if (!repair.step()) return false;
        var value = repair.counts();
        repair.close();
        repair = null;
        if (value != null || ++attempts >= 4) return finish(value, false);
        return false;
    }

    private boolean finish(BigInteger[] value, boolean impossible) {
        counts = value == null ? null : reduction.expand(value);
        infeasible = impossible && !trial;
        complete = true;
        return true;
    }

    BigInteger[] counts() {
        return counts;
    }

    boolean infeasible() {
        return infeasible;
    }

    List<CountConflict> learnedConflicts() {
        return List.copyOf(learned);
    }

    @Override
    public void close() {
        if (bounds != null) bounds.close();
        if (reduction != null) reduction.close();
        if (partition != null) partition.close();
        if (components != null) components.close();
        if (matching != null) matching.close();
        if (binary != null) binary.close();
        if (linear != null) linear.close();
        if (repair != null) repair.close();
        budget.release(memory);
        memory = 0;
    }
}
