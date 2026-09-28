package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/**
 * Relaxation-enforced neighborhoods, following SCIP's RENS heuristic. Integral
 * LP coordinates are initially fixed, fractional ones get floor/ceil domains.
 * Later neighborhoods release connected coordinates. Only verified witnesses
 * escape; failure of a restricted neighborhood proves nothing about the order.
 */
final class CountNeighborhood implements AutoCloseable {

    private final List<ExactLinearProgram.Constraint> rows;
    private final BigInteger[] lower, upper;
    private final ExactRational[] point;
    private final PlanningBudget budget;
    private final List<Integer> release = new ArrayList<>();
    private final long allowance;
    private CountCdcl search;
    private CountFeasibilityPump pump;
    private CountDomainSearch incumbentSearch;
    private CountSoftSearch softSearch;
    private BigInteger[] incumbent, operationCosts;
    private int incumbentAttempt;
    private long memory, work;
    private int attempt;
    private BigInteger[] counts;
    private boolean complete;
    private boolean allowPump = true;

    CountNeighborhood pump(boolean enabled) {
        allowPump = enabled;
        return this;
    }

    CountNeighborhood incumbent(BigInteger[] values, BigInteger[] costs) {
        if (values != null && values.length == lower.length) {
            incumbent = values.clone();
            operationCosts = costs.clone();
        }
        return this;
    }

    CountNeighborhood(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper,
                      ExactRational[] point, PlanningBudget budget) {
        this.rows = rows;
        this.lower = lower;
        this.upper = upper;
        this.point = point;
        this.budget = budget;
        allowance = Math.min(131_072, budget.remainingWork() / 16);
        if (point == null || lower.length > 256 || rows.size() > 1024 || allowance < 4096) {
            complete = true;
            return;
        }
        long bytes = 1024 + 384L * lower.length;
        if (!budget.tryReserve(bytes)) {
            complete = true;
            return;
        }
        memory = bytes;
        int[] connections = new int[lower.length];
        for (var row : rows) {
            boolean fractional = row.terms().keySet().stream().anyMatch(i -> !point[i].integral());
            for (int id : row.terms().keySet()) connections[id] += fractional ? 4 : 1;
        }
        for (int i = 0; i < point.length; i++) if (!lower[i].equals(upper[i]) && point[i].integral()) release.add(i);
        release.sort(Comparator.<Integer>comparingInt(i -> -connections[i]).thenComparingInt(i -> i));
    }

    boolean step() {
        if (complete) return true;
        long before = budget.threadWork();
        try {
            budget.check();
            if (work >= allowance) return finish("neighborhoods_unresolved");
            if (incumbent != null && incumbentAttempt < 3) return improveIncumbent();
            if (attempt == 4) {
                if (!allowPump) return finish("root_heuristic_budget_spent");
                if (pump == null) pump = new CountFeasibilityPump(rows, lower, upper, point, budget, Math.min(32768, allowance - work));
                if (!pump.step()) return false;
                counts = pump.counts();
                pump.close();
                pump = null;
                return finish(counts == null ? "neighborhoods_unresolved" : "verified_pump_witness");
            }
            if (search == null) {
                BigInteger[] low = lower.clone(), high = upper.clone();
                Set<Integer> free = new HashSet<>(release.subList(0, Math.min(release.size(), attempt * 8)));
                for (int i = 0; i < low.length; i++) {
                    budget.check();
                    BigInteger l = point[i].floor(), h = point[i].ceil();
                    if (point[i].integral() && free.contains(i)) {
                        boolean canUp = upper[i] == null || h.compareTo(upper[i]) < 0;
                        boolean canDown = l.compareTo(lower[i]) > 0;
                        if (canUp && (!canDown || attempt % 2 == 1)) h = h.add(BigInteger.ONE);
                        else if (canDown) l = l.subtract(BigInteger.ONE);
                    }
                    low[i] = l.max(lower[i]);
                    high[i] = upper[i] == null ? h : h.min(upper[i]);
                    if (high[i].compareTo(low[i]) < 0) return finish("point_outside_domain");
                }
                search = new CountCdcl(rows, low, high, budget, Math.min(32768, allowance - work));
            }
            if (!search.step()) return false;
            counts = search.counts();
            search.close();
            search = null;
            attempt++;
            if (counts != null) return finish("verified_witness");
            return false;
        } finally {
            work += budget.threadWork() - before;
        }
    }

    private boolean improveIncumbent() {
        if (incumbentSearch == null && softSearch == null) {
            var constraints = new ArrayList<>(rows);
            var objective = new LinkedHashMap<Integer, BigInteger>();
            BigInteger old = BigInteger.ZERO;
            BigInteger[] low = lower.clone(), high = upper.clone();
            Set<Integer> free = new HashSet<>(release.subList(0, Math.min(8, release.size())));
            for (int i = 0; i < low.length; i++) {
                budget.check();
                if (operationCosts[i].signum() != 0) objective.put(i, operationCosts[i]);
                old = old.add(operationCosts[i].multiply(incumbent[i]));
                // Open-ended counts get a local, explicitly speculative domain.
                if (high[i] == null) high[i] = low[i].max(incumbent[i]).add(BigInteger.valueOf(32));
                boolean fix = incumbentAttempt == 0 ? point[i].integral() && point[i].numerator().equals(incumbent[i]) : incumbentAttempt == 1 && !free.contains(i);
                if (fix && incumbent[i].compareTo(low[i]) >= 0 && incumbent[i].compareTo(high[i]) <= 0) low[i] = high[i] = incumbent[i];
            }
            constraints.add(new ExactLinearProgram.Constraint(objective, old.subtract(BigInteger.ONE)));
            if (incumbentAttempt < 2) incumbentSearch = new CountDomainSearch(constraints, low, high, budget, Math.min(16384, allowance - work));
            else {
                List<CountSoftSearch.Soft> preferences = new ArrayList<>();
                for (int i = 0; i < low.length; i++) {
                    preferences.add(new CountSoftSearch.Soft(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE), incumbent[i]), BigInteger.ONE));
                    preferences.add(new CountSoftSearch.Soft(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE.negate()), incumbent[i].negate()), BigInteger.ONE));
                }
                softSearch = new CountSoftSearch(constraints, low, high, preferences, budget, Math.min(32768, allowance - work));
            }
        }
        if (incumbentSearch != null) {
            if (!incumbentSearch.step()) return false;
            counts = incumbentSearch.counts();
            incumbentSearch.close();
            incumbentSearch = null;
        } else {
            if (!softSearch.step()) return false;
            counts = softSearch.counts();
            softSearch.close();
            softSearch = null;
        }
        incumbentAttempt++;
        if (counts != null) return finish("verified_incumbent_neighborhood; stage=" + incumbentAttempt);
        // UNSAT here concerns only a RINS/LNS/soft neighborhood, never the order.
        return false;
    }

    private boolean finish(String detail) {
        complete = true;
        budget.note("count_neighborhood", detail + "; attempts=" + attempt + "; work=" + work + "; original_domain_retained");
        return true;
    }

    BigInteger[] counts() {
        return counts == null ? null : counts.clone();
    }

    @Override
    public void close() {
        if (incumbentSearch != null) incumbentSearch.close();
        if (softSearch != null) softSearch.close();
        incumbentSearch = null;
        softSearch = null;
        if (search != null) search.close();
        if (pump != null) pump.close();
        pump = null;
        search = null;
        budget.release(memory);
        memory = 0;
    }
}
