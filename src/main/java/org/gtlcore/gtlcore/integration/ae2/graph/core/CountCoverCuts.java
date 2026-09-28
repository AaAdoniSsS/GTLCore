package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/**
 * Minimal knapsack-cover separation, as used in SCIP/OR-Tools cutting planes.
 * A set of Boolean choices whose combined minimum consumption exceeds a row's
 * capacity cannot all be taken. Only cuts violated by the exact LP point are
 * retained; the original weighted rows and all integer choices stay present.
 */
final class CountCoverCuts implements AutoCloseable {

    private record Literal(int id, int sign, BigInteger weight, BigInteger offset, ExactRational value) {}

    private final List<ExactLinearProgram.Constraint> rows;
    private final BigInteger[] lower, upper;
    private final ExactRational[] point;
    private final PlanningBudget budget;
    private final List<ExactLinearProgram.Constraint> cuts = new ArrayList<>();
    private final Set<ExactLinearProgram.Constraint> known;
    private final Set<ExactLinearProgram.Constraint> lifted = new HashSet<>();
    private final long allowance;
    private long work, memory;
    private int cursor;
    private boolean complete;

    CountCoverCuts(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper,
                   ExactRational[] point, PlanningBudget budget) {
        this.rows = rows;
        this.lower = lower;
        this.upper = upper;
        this.point = point;
        this.budget = budget;
        known = new HashSet<>(rows);
        allowance = Math.min(32768, budget.remainingWork() / 32);
        long bytes = 2048 + 256L * lower.length + 192L * rows.size();
        if (lower.length > 512 || rows.size() > 1024 || allowance < 1024 || !budget.tryReserve(bytes)) complete = true;
        else memory = bytes;
    }

    boolean step() {
        if (complete) return true;
        charge();
        if (cursor == rows.size() || work >= allowance || cuts.size() >= 8) return finish();
        var row = rows.get(cursor++);
        if (row.terms().size() < 2 || row.terms().size() > 256) return false;
        BigInteger minimum = BigInteger.ZERO;
        List<Literal> literals = new ArrayList<>();
        for (var term : row.terms().entrySet()) {
            charge();
            int id = term.getKey(), sign = term.getValue().signum();
            if (sign == 0) continue;
            BigInteger endpoint = sign > 0 ? lower[id] : upper[id];
            if (endpoint == null) return false;
            minimum = minimum.add(term.getValue().multiply(endpoint));
            if (upper[id] == null || !upper[id].subtract(lower[id]).equals(BigInteger.ONE)) continue;
            BigInteger offset = sign > 0 ? lower[id].negate() : upper[id];
            ExactRational value = point[id].multiply(ExactRational.of(BigInteger.valueOf(sign))).add(ExactRational.of(offset));
            if (value.compareTo(ExactRational.ZERO) < 0 || value.compareTo(ExactRational.ONE) > 0) return false;
            literals.add(new Literal(id, sign, term.getValue().abs(), offset, value));
        }
        BigInteger capacity = row.upper().subtract(minimum);
        if (capacity.signum() < 0 || literals.size() < 2) return false;
        for (int pass = 0; pass < 3 && work < allowance && cuts.size() < 8; pass++) {
            if (pass == 0) literals.sort((a, b) -> {
                int c = ExactRational.ONE.subtract(a.value()).multiply(ExactRational.of(b.weight()))
                        .compareTo(ExactRational.ONE.subtract(b.value()).multiply(ExactRational.of(a.weight())));
                return c != 0 ? c : Integer.compare(a.id(), b.id());
            });
            else if (pass == 1) literals.sort(Comparator.comparing(Literal::value).reversed().thenComparingInt(Literal::id));
            else literals.sort(Comparator.comparing(Literal::weight).reversed().thenComparingInt(Literal::id));
            List<Literal> cover = new ArrayList<>();
            BigInteger weight = BigInteger.ZERO;
            for (Literal literal : literals) {
                charge();
                cover.add(literal);
                weight = weight.add(literal.weight());
                if (weight.compareTo(capacity) > 0) break;
            }
            if (weight.compareTo(capacity) <= 0) continue;
            cover.sort(Comparator.comparing(Literal::value).thenComparing(Literal::weight));
            for (var it = cover.iterator(); it.hasNext();) {
                charge();
                var literal = it.next();
                if (weight.subtract(literal.weight()).compareTo(capacity) > 0) {
                    weight = weight.subtract(literal.weight());
                    it.remove();
                }
            }
            // The exact sum, not a floating-point tolerance, separates the cut.
            ExactRational activity = ExactRational.ZERO;
            BigInteger bound = BigInteger.valueOf(cover.size() - 1L);
            Map<Integer, BigInteger> terms = new LinkedHashMap<>();
            for (Literal literal : cover) {
                charge();
                activity = activity.add(literal.value());
                bound = bound.subtract(literal.offset());
                terms.put(literal.id(), BigInteger.valueOf(literal.sign()));
            }
            var cut = new ExactLinearProgram.Constraint(terms, bound);
            if (activity.compareTo(ExactRational.of(BigInteger.valueOf(cover.size() - 1L))) > 0 && known.add(cut)) cuts.add(cut);
            if (cuts.size() < 8) lift(row, literals, cover, capacity);
        }
        return false;
    }

    /** Sequential lifting uses profit-indexed DP, so long capacities are never enumerated. */
    private void lift(ExactLinearProgram.Constraint source, List<Literal> all, List<Literal> cover, BigInteger capacity) {
        int rhs = cover.size() - 1;
        if (rhs <= 0 || cover.size() > 64 || all.size() > 96) return;
        Map<Literal, Integer> coefficients = new LinkedHashMap<>();
        cover.forEach(literal -> coefficients.put(literal, 1));
        int total = cover.size();
        boolean changed = false;
        for (Literal extra : all) {
            charge();
            if (coefficients.containsKey(extra)) continue;
            long effort = (long) (total + 1) * (coefficients.size() + 1);
            if (total > 8192 || effort > allowance - work) break;
            long bytes = 96L * (total + 1);
            if (!budget.tryReserve(bytes)) break;
            int maximum = -1;
            try {
                BigInteger[] minimum = new BigInteger[total + 1];
                minimum[0] = BigInteger.ZERO;
                int used = 0;
                for (var term : coefficients.entrySet()) {
                    int profit = term.getValue();
                    for (int p = used; p >= 0; p--) {
                        charge();
                        if (minimum[p] == null) continue;
                        BigInteger weight = minimum[p].add(term.getKey().weight());
                        if (minimum[p + profit] == null || weight.compareTo(minimum[p + profit]) < 0) minimum[p + profit] = weight;
                    }
                    used += profit;
                }
                BigInteger available = capacity.subtract(extra.weight());
                for (int p = total; p >= 0; p--) {
                    charge();
                    if (minimum[p] != null && minimum[p].compareTo(available) <= 0) {
                        maximum = p;
                        break;
                    }
                }
            } finally {
                budget.release(bytes);
            }
            // An individually impossible choice may receive any coefficient;
            // rhs+1 suffices and remains easy for the independent checker.
            int coefficient = rhs - maximum;
            if (coefficient > 0) {
                coefficients.put(extra, coefficient);
                total += coefficient;
                changed = true;
            }
        }
        if (!changed) return;
        BigInteger limit = BigInteger.valueOf(rhs);
        ExactRational activity = ExactRational.ZERO;
        Map<Integer, BigInteger> terms = new LinkedHashMap<>();
        for (var term : coefficients.entrySet()) {
            charge();
            Literal literal = term.getKey();
            BigInteger weight = BigInteger.valueOf(term.getValue());
            activity = activity.add(literal.value().multiply(ExactRational.of(weight)));
            limit = limit.subtract(literal.offset().multiply(weight));
            terms.put(literal.id(), weight.multiply(BigInteger.valueOf(literal.sign())));
        }
        var cut = new ExactLinearProgram.Constraint(terms, limit);
        if (activity.compareTo(ExactRational.of(BigInteger.valueOf(rhs))) <= 0 || !known.add(cut)) return;
        cuts.add(cut);
        lifted.add(cut);
        if (budget.proofJournal() != null) budget.proofJournal().add(new CountProof.Knapsack("sequential_cover_lifting", lower.length,
                CountProof.row(source), Arrays.asList(lower), Arrays.asList(upper), CountProof.row(cut)));
    }

    private void charge() {
        budget.check();
        work++;
    }

    private boolean finish() {
        complete = true;
        if (!cuts.isEmpty()) {
            if (budget.proofJournal() != null) {
                var scope = new ArrayList<>(rows);
                for (int i = 0; i < lower.length; i++) {
                    scope.add(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE.negate()), lower[i].negate()));
                    if (upper[i] != null) scope.add(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE), upper[i]));
                }
                List<CountConflict> forbidden = new ArrayList<>();
                for (var cut : cuts) {
                    if (lifted.contains(cut)) continue;
                    Map<Integer, BigInteger> opposite = new LinkedHashMap<>();
                    cut.terms().forEach((id, value) -> opposite.put(id, value.negate()));
                    forbidden.add(new CountConflict(List.of(new ExactLinearProgram.Constraint(opposite, cut.upper().negate().subtract(BigInteger.ONE)))));
                }
                budget.proofJournal().add(CountProof.certificate("knapsack_cover", lower.length, scope, forbidden, null, false));
            }
            budget.note("count_cover_cuts", "violated_covers=" + cuts.size() + "; lifted=" + lifted.size() + "; work=" + work);
        }
        return true;
    }

    List<ExactLinearProgram.Constraint> cuts() {
        return List.copyOf(cuts);
    }

    @Override
    public void close() {
        budget.release(memory);
        memory = 0;
    }
}
