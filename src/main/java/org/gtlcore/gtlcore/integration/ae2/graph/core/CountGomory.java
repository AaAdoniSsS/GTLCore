package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/**
 * Tableau-guided pure-integer Gomory separation. Recipe counts and slacks of
 * integral material rows are integers. Fractional slack multipliers yield a
 * nonnegative rank-one CG combination, independently replayed by CountProof.
 */
final class CountGomory {

    private CountGomory() {}

    static List<ExactLinearProgram.Constraint> separate(ExactLinearProgram.Basis basis, ExactRational[] point, PlanningBudget budget) {
        if (basis == null || basis.table == null || point == null || basis.variables > 128 || basis.constraints.size() > 512) return List.of();
        long allowance = Math.min(32768, budget.remainingWork() / 32), started = budget.threadWork();
        if (allowance < 2048) return List.of();
        long bytes = 1024L + 512L * (basis.variables + basis.constraints.size());
        if (!budget.tryReserve(bytes)) return List.of();
        List<ExactLinearProgram.Constraint> result = new ArrayList<>();
        try {
            for (int r = 0; r < basis.basic.length && result.size() < 4 && budget.threadWork() - started < allowance; r++) {
                budget.check();
                if (basis.basic[r] < 0 || basis.table[r][basis.variables + 1].integral()) continue;
                ExactRational[] fractions = new ExactRational[basis.constraints.size()];
                Arrays.fill(fractions, ExactRational.ZERO);
                BigInteger divisor = BigInteger.ONE;
                for (int j = 0; j < basis.nonbasic.length; j++) {
                    budget.check();
                    int id = basis.nonbasic[j] - basis.variables;
                    if (id < 0) continue;
                    ExactRational value = basis.table[r][j];
                    value = value.subtract(ExactRational.of(value.floor()));
                    fractions[id] = value;
                    divisor = divisor.multiply(value.denominator().divide(divisor.gcd(value.denominator())));
                }
                if (divisor.bitLength() > 256 || divisor.equals(BigInteger.ONE)) continue;
                List<BigInteger> weights = new ArrayList<>();
                Map<Integer, BigInteger> sum = new TreeMap<>();
                BigInteger bound = BigInteger.ZERO;
                for (int i = 0; i < fractions.length; i++) {
                    budget.check();
                    BigInteger weight = fractions[i].numerator().multiply(divisor.divide(fractions[i].denominator()));
                    weights.add(weight);
                    if (weight.signum() == 0) continue;
                    var row = basis.constraints.get(i);
                    bound = bound.add(row.upper().multiply(weight));
                    for (var term : row.terms().entrySet()) {
                        budget.check();
                        sum.merge(term.getKey(), term.getValue().multiply(weight), BigInteger::add);
                    }
                }
                Map<Integer, BigInteger> terms = new TreeMap<>();
                for (var term : sum.entrySet()) {
                    budget.check();
                    BigInteger value = floor(term.getValue(), divisor);
                    if (value.signum() != 0) terms.put(term.getKey(), value);
                }
                var cut = new ExactLinearProgram.Constraint(terms, floor(bound, divisor));
                ExactRational activity = ExactRational.ZERO;
                for (var term : terms.entrySet()) {
                    budget.check();
                    activity = activity.add(point[term.getKey()].multiply(ExactRational.of(term.getValue())));
                }
                if (activity.compareTo(ExactRational.of(cut.upper())) <= 0 || result.contains(cut) || basis.constraints.contains(cut)) continue;
                result.add(cut);
                if (budget.proofJournal() != null) {
                    List<CountProof.Row> axioms = new ArrayList<>(basis.constraints.stream().map(CountProof::row).toList());
                    for (int id = 0; id < basis.variables; id++) {
                        axioms.add(new CountProof.Row(Map.of(id, BigInteger.ONE.negate()), BigInteger.ZERO));
                        weights.add(BigInteger.ZERO);
                    }
                    budget.proofJournal().add(new CountProof.Rounding("tableau_gomory", basis.variables, axioms, weights, divisor,
                            Collections.nCopies(basis.variables, BigInteger.ZERO), CountProof.row(cut)));
                }
            }
        } catch (ExactRational.PrecisionLimit limit) {
            // Previously completed cuts remain valid; precision is not a proof.
        } finally {
            budget.release(bytes);
        }
        if (!result.isEmpty()) budget.note("count_gomory", "violated_tableau_cuts=" + result.size() + "; work=" + (budget.threadWork() - started));
        return result;
    }

    private static BigInteger floor(BigInteger n, BigInteger d) {
        var qr = n.divideAndRemainder(d);
        return qr[1].signum() < 0 ? qr[0].subtract(BigInteger.ONE) : qr[0];
    }
}
