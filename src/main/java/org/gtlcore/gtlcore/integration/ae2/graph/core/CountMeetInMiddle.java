package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Exact multidimensional binary equalities; range-independent, but state-budgeted. */
final class CountMeetInMiddle implements AutoCloseable {

    private static final int MAX_HALF = 18, MAX_DIMENSIONS = 8;

    private static final class LocalLimit extends RuntimeException {

        LocalLimit() {
            super(null, null, false, false);
        }
    }

    private final List<ExactLinearProgram.Constraint> original;
    private final Map<Map<Integer, BigInteger>, BigInteger> rows = new LinkedHashMap<>();
    private final Map<List<BigInteger>, Integer> left = new HashMap<>();
    private final PlanningBudget budget;
    private final BigInteger[] lower, upper;
    private final int[] choices;
    private final long allowance;
    private BigInteger[][] coefficients;
    private BigInteger[] goal, sum, counts;
    private long memory, work;
    private int phase, rowIndex, split, index, mask, leftStates, rightStates, trials;
    private boolean complete, infeasible;

    CountMeetInMiddle(List<ExactLinearProgram.Constraint> original, BigInteger[] lower,
                      BigInteger[] upper, PlanningBudget budget) {
        this.original = original;
        this.lower = lower.clone();
        this.upper = upper.clone();
        this.budget = budget;
        allowance = Math.min(2_097_152, budget.remainingWork() / 4);
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < lower.length; i++) {
            if (lower[i].equals(upper[i])) continue;
            if (lower[i].signum() != 0 || !BigInteger.ONE.equals(upper[i])) complete = true;
            free.add(i);
        }
        // A larger Boolean model may still yield a cheap witness on one face.
        // Keep this heuristic local: its exhausted domain is NEVER a proof for
        // the parent model. General search retains all the omitted choices.
        if (free.size() <= 128) while (free.size() > MAX_HALF * 2) {
            int id = free.remove(0);
            this.upper[id] = this.lower[id];
            trials++;
        }
        choices = free.stream().mapToInt(Integer::intValue).toArray();
        if (complete || choices.length == 0 || choices.length > MAX_HALF * 2 || allowance < 1024) {
            complete = true;
            return;
        }
        long entries = original.stream().mapToLong(row -> row.terms().size()).sum();
        long bytes = 1024 + 192L * entries + 256L * original.size() + 256L * lower.length;
        if (budget.tryReserve(bytes)) memory = bytes;
        else complete = true;
    }

    boolean step() {
        if (complete) return true;
        try {
            charge();
            switch (phase) {
                case 0 -> {
                    if (rowIndex == original.size()) return prepare();
                    var row = original.get(rowIndex++);
                    Map<Integer, BigInteger> terms = new TreeMap<>();
                    BigInteger bound = row.upper(), minimum = BigInteger.ZERO, maximum = BigInteger.ZERO;
                    for (var term : row.terms().entrySet()) {
                        charge();
                        int id = term.getKey();
                        if (lower[id].equals(upper[id])) bound = bound.subtract(term.getValue().multiply(lower[id]));
                        else if (term.getValue().signum() != 0) {
                            terms.put(id, term.getValue());
                            minimum = minimum.add(term.getValue().min(BigInteger.ZERO));
                            maximum = maximum.add(term.getValue().max(BigInteger.ZERO));
                        }
                    }
                    if (minimum.compareTo(bound) > 0) return finish(true, "bounds_infeasible");
                    if (maximum.compareTo(bound) <= 0) return false;
                    var normalized = CountReduction.normalize(new ExactLinearProgram.Constraint(terms, bound));
                    rows.merge(normalized.terms(), normalized.upper(), BigInteger::min);
                }
                case 1 -> {
                    left.putIfAbsent(List.of(sum.clone()), mask);
                    if (++index == leftStates) {
                        index = mask = 0;
                        Arrays.fill(sum, BigInteger.ZERO);
                        phase = 2;
                    } else advanceMask(0);
                }
                case 2 -> {
                    BigInteger[] complement = new BigInteger[goal.length];
                    for (int d = 0; d < goal.length; d++) {
                        charge();
                        complement[d] = goal[d].subtract(sum[d]);
                    }
                    Integer matched = left.get(List.of(complement));
                    if (matched != null) {
                        counts = lower.clone();
                        for (int i = 0; i < choices.length; i++) counts[choices[i]] = BigInteger.valueOf(i < split ? (matched >>> i) & 1 : (mask >>> (i - split)) & 1);
                        rowIndex = 0;
                        phase = 3;
                    } else if (++index == rightStates) return finish(true, "exhaustive_infeasible");
                    else advanceMask(split);
                }
                case 3 -> {
                    // Matching uses only proved equalities. Still independently
                    // replay every untouched row before exporting a witness.
                    if (rowIndex == original.size()) return finish(false, "witness");
                    var row = original.get(rowIndex++);
                    BigInteger total = BigInteger.ZERO;
                    for (var term : row.terms().entrySet()) {
                        charge();
                        total = total.add(term.getValue().multiply(counts[term.getKey()]));
                    }
                    if (total.compareTo(row.upper()) > 0) throw new IllegalStateException("Meet-in-middle witness violates original row");
                }
                default -> throw new IllegalStateException("Invalid count matching phase");
            }
            return false;
        } catch (LocalLimit limit) {
            counts = null;
            return finish(false, "work_limit");
        }
    }

    private boolean prepare() {
        List<Map<Integer, BigInteger>> dimensions = new ArrayList<>();
        List<BigInteger> targets = new ArrayList<>();
        for (var row : rows.entrySet()) {
            Map<Integer, BigInteger> opposite = new TreeMap<>();
            for (var term : row.getKey().entrySet()) {
                charge();
                opposite.put(term.getKey(), term.getValue().negate());
            }
            // Keep the general Boolean solver for inequalities. Deduplicating
            // equal signatures here is sound only when these equalities capture
            // every remaining nontrivial constraint, including shared resources.
            if (!row.getValue().negate().equals(rows.get(opposite))) return finish(false, "not_equalities");
            int first = Collections.min(row.getKey().keySet());
            if (row.getKey().get(first).signum() < 0) continue;
            dimensions.add(row.getKey());
            targets.add(row.getValue());
        }
        if (dimensions.isEmpty() || dimensions.size() > MAX_DIMENSIONS) return finish(false, "dimension_limit");
        split = choices.length / 2;
        leftStates = 1 << split;
        rightStates = 1 << (choices.length - split);
        coefficients = new BigInteger[choices.length][dimensions.size()];
        int bits = 1;
        for (int i = 0; i < choices.length; i++) for (int d = 0; d < dimensions.size(); d++) {
            charge();
            coefficients[i][d] = dimensions.get(d).getOrDefault(choices[i], BigInteger.ZERO);
            bits = Math.max(bits, coefficients[i][d].abs().bitLength());
        }
        long bytes = (128L + dimensions.size() * (64L + (bits + MAX_HALF + 7L) / 8)) * leftStates;
        if (!budget.tryReserve(bytes)) return finish(false, "memory_limit");
        memory += bytes;
        goal = targets.toArray(BigInteger[]::new);
        sum = new BigInteger[goal.length];
        Arrays.fill(sum, BigInteger.ZERO);
        phase = 1;
        return false;
    }

    /** Gray enumeration updates just one selected source per combination. */
    private void advanceMask(int offset) {
        int next = index ^ (index >>> 1);
        int bit = Integer.numberOfTrailingZeros(next ^ mask);
        boolean add = (next & (1 << bit)) != 0;
        for (int d = 0; d < sum.length; d++) {
            charge();
            sum[d] = add ? sum[d].add(coefficients[offset + bit][d]) : sum[d].subtract(coefficients[offset + bit][d]);
        }
        mask = next;
    }

    private void charge() {
        budget.check();
        if (++work > allowance) throw new LocalLimit();
    }

    private boolean finish(boolean impossible, String detail) {
        infeasible = impossible && trials == 0;
        complete = true;
        budget.note("count_match", detail + "; choices=" + choices.length + "; dimensions=" + (goal == null ? 0 : goal.length) +
                "; left_states=" + leftStates + "; right_states=" + rightStates + "; trial_counts=" + trials +
                "; proven_infeasible=" + infeasible + "; work=" + work);
        return true;
    }

    BigInteger[] counts() {
        return complete && counts != null ? counts.clone() : null;
    }

    boolean infeasible() {
        return complete && infeasible;
    }

    @Override
    public void close() {
        left.clear();
        rows.clear();
        budget.release(memory);
        memory = 0;
    }
}
