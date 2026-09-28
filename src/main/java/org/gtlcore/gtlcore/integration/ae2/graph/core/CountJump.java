package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/**
 * Bounded feasibility-jump search over exact integer counts. This is a local
 * implementation of the weighted-violation and incremental-jump approach used
 * by OR-Tools' feasibility_jump.cc, not an infeasibility prover. Floating point
 * only orders candidate moves; domains, activities and the accepted witness are
 * checked with mathematical integers. No learned constraint escapes this search.
 */
final class CountJump implements AutoCloseable {

    private record Term(int row, BigInteger coefficient) {}

    private final List<ExactLinearProgram.Constraint> rows;
    private final BigInteger[] lower, upper, values, residual, scales, jumps;
    private final double[] weights, scores;
    private final List<List<Term>> affected = new ArrayList<>();
    private final BitSet dirty = new BitSet(), violated = new BitSet();
    private final PlanningBudget budget;
    private final long allowance;
    private final SplittableRandom random = new SplittableRandom(0x49f6b58dL);
    private BigInteger[] counts;
    private long memory, work;
    private int initialized, moves, bumps, restarts, stale, pairs, last = -1;
    private boolean complete, pairMode;

    CountJump(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper,
              PlanningBudget budget, long allowance) {
        this.rows = rows;
        this.lower = lower.clone();
        this.upper = upper.clone();
        this.budget = budget;
        this.allowance = Math.min(allowance, budget.remainingWork() / 8);
        values = lower.clone();
        residual = new BigInteger[rows.size()];
        scales = new BigInteger[rows.size()];
        weights = new double[rows.size()];
        jumps = new BigInteger[lower.length];
        scores = new double[lower.length];
        long entries = rows.stream().mapToLong(row -> row.terms().size()).sum();
        long bytes = 2048 + 256L * lower.length + 256L * rows.size() + 96L * entries;
        if (lower.length > 512 || rows.size() > 2048 || entries > 32768 || this.allowance < 1024 || !budget.tryReserve(bytes)) {
            complete = true;
            return;
        }
        memory = bytes;
        for (int i = 0; i < lower.length; i++) affected.add(new ArrayList<>());
        Arrays.fill(weights, 1.0);
    }

    boolean step() {
        if (complete) return true;
        charge();
        if (work >= allowance) return finish("work_limit");
        if (!pairMode && values.length <= 128 && initialized == rows.size() && work >= allowance * 3 / 4) {
            // A second heuristic starts from the same domains. Its failures
            // have no bearing on any unvisited count or execution ordering.
            pairMode = true;
            System.arraycopy(lower, 0, values, 0, lower.length);
            Arrays.fill(weights, 1.0);
            affected.forEach(List::clear);
            dirty.clear();
            violated.clear();
            initialized = 0;
            stale = 0;
            last = -1;
            return false;
        }
        if (initialized < rows.size()) {
            int r = initialized++;
            var row = rows.get(r);
            BigInteger value = row.upper().negate(), scale = BigInteger.ONE;
            for (var term : row.terms().entrySet()) {
                charge();
                value = value.add(term.getValue().multiply(values[term.getKey()]));
                scale = scale.max(term.getValue().abs());
                if (!lower[term.getKey()].equals(upper[term.getKey()]) && term.getValue().signum() != 0)
                    affected.get(term.getKey()).add(new Term(r, term.getValue()));
            }
            residual[r] = value;
            scales[r] = scale;
            violated.set(r, value.signum() > 0);
            if (initialized == rows.size()) dirty.set(0, lower.length);
            return false;
        }
        if (violated.isEmpty()) {
            // Re-evaluate the original rows instead of trusting cached deltas.
            for (int i = 0; i < values.length; i++) {
                charge();
                if (values[i].compareTo(lower[i]) < 0 || upper[i] != null && values[i].compareTo(upper[i]) > 0)
                    throw new IllegalStateException("Jump count outside domain");
            }
            for (var row : rows) {
                BigInteger sum = BigInteger.ZERO;
                for (var term : row.terms().entrySet()) {
                    charge();
                    sum = sum.add(term.getValue().multiply(values[term.getKey()]));
                }
                if (sum.compareTo(row.upper()) > 0) throw new IllegalStateException("Jump count violates original row");
            }
            counts = values.clone();
            return finish("verified_witness");
        }
        int next = dirty.nextSetBit(0);
        if (next >= 0) {
            dirty.clear(next);
            recompute(next);
            return false;
        }
        int best = -1;
        for (int i = 0; i < scores.length; i++) {
            charge();
            if (jumps[i] != null && (best < 0 || scores[i] < scores[best] || scores[i] == scores[best] && i != last && best == last)) best = i;
        }
        if (best >= 0 && scores[best] < -1e-12) {
            move(best, jumps[best]);
            stale = 0;
        } else if (best >= 0 && pairMode && pair()) {
            stale = 0;
        } else {
            bump();
            if (++stale >= 4 || bumps % 32 == 0) {
                perturb();
                stale = 0;
            }
        }
        return false;
    }

    private void recompute(int variable) {
        jumps[variable] = null;
        scores[variable] = Double.POSITIVE_INFINITY;
        if (affected.get(variable).isEmpty()) return;
        if (binary(variable)) {
            jumps[variable] = values[variable].equals(lower[variable]) ? upper[variable] : lower[variable];
            scores[variable] = score(variable, jumps[variable]);
            return;
        }
        Set<BigInteger> candidates = new TreeSet<>();
        candidates.add(lower[variable]);
        if (upper[variable] != null) candidates.add(upper[variable]);
        candidates.add(clamp(variable, values[variable].subtract(BigInteger.ONE)));
        candidates.add(clamp(variable, values[variable].add(BigInteger.ONE)));
        for (Term term : affected.get(variable)) {
            charge();
            // Every piecewise-linear hinge contributes its adjacent integers.
            BigInteger at = floorDivide(residual[term.row()].negate(), term.coefficient());
            candidates.add(clamp(variable, values[variable].add(at)));
            candidates.add(clamp(variable, values[variable].add(at).add(BigInteger.ONE)));
        }
        candidates.remove(values[variable]);
        for (BigInteger value : candidates) {
            double score = score(variable, value);
            if (score < scores[variable]) {
                scores[variable] = score;
                jumps[variable] = value;
            }
        }
    }

    private double score(int variable, BigInteger value) {
        BigInteger delta = value.subtract(values[variable]);
        double score = 0.0;
        for (Term term : affected.get(variable)) {
            charge();
            int r = term.row();
            BigInteger changed = residual[r].add(term.coefficient().multiply(delta));
            BigInteger difference = changed.max(BigInteger.ZERO).subtract(residual[r].max(BigInteger.ZERO));
            score += weights[r] * ratio(difference, scales[r]);
        }
        return score;
    }

    private void move(int variable, BigInteger value) {
        BigInteger delta = value.subtract(values[variable]);
        values[variable] = value;
        last = variable;
        moves++;
        dirty.set(variable);
        for (Term term : affected.get(variable)) {
            charge();
            int r = term.row();
            BigInteger old = residual[r];
            residual[r] = old.add(term.coefficient().multiply(delta));
            violated.set(r, residual[r].signum() > 0);
            for (var entry : rows.get(r).terms().entrySet()) {
                charge();
                int id = entry.getKey();
                if (dirty.get(id)) continue;
                if (binary(id) && jumps[id] != null) {
                    BigInteger change = entry.getValue().multiply(jumps[id].subtract(values[id]));
                    BigInteger previous = old.add(change).max(BigInteger.ZERO).subtract(old.max(BigInteger.ZERO));
                    BigInteger next = residual[r].add(change).max(BigInteger.ZERO).subtract(residual[r].max(BigInteger.ZERO));
                    scores[id] += weights[r] * ratio(next.subtract(previous), scales[r]);
                } else dirty.set(id);
            }
        }
        if (moves % 128 == 0) dirty.set(0, values.length);
    }

    private void bump() {
        bumps++;
        for (int r = violated.nextSetBit(0); r >= 0; r = violated.nextSetBit(r + 1)) {
            charge();
            weights[r] += 1.0;
            for (var entry : rows.get(r).terms().entrySet()) {
                charge();
                int id = entry.getKey();
                if (dirty.get(id)) continue;
                if (binary(id) && jumps[id] != null) {
                    BigInteger next = residual[r].add(entry.getValue().multiply(jumps[id].subtract(values[id])));
                    scores[id] += ratio(next.max(BigInteger.ZERO).subtract(residual[r].max(BigInteger.ZERO)), scales[r]);
                } else dirty.set(id);
            }
        }
    }

    private boolean binary(int id) {
        return upper[id] != null && upper[id].subtract(lower[id]).equals(BigInteger.ONE);
    }

    /** Cross a one-coordinate barrier without committing an expensive prefix. */
    private boolean pair() {
        if (values.length > 128) return false;
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < values.length; i++) if (jumps[i] != null) choices.add(i);
        choices.sort(Comparator.comparingDouble((Integer i) -> scores[i]).thenComparingInt(i -> i));
        BigInteger[] proposed = jumps.clone();
        double[] firstScores = scores.clone();
        int previous = last;
        for (int k = 0; k < Math.min(6, choices.size()) && work < allowance; k++) {
            int first = choices.get(k);
            BigInteger original = values[first];
            move(first, proposed[first]);
            for (int second = 0; second < values.length && work < allowance; second++) {
                charge();
                if (second == first || affected.get(second).isEmpty()) continue;
                recompute(second);
                if (jumps[second] != null && firstScores[first] + scores[second] < -1e-12) {
                    move(second, jumps[second]);
                    pairs++;
                    return true;
                }
            }
            move(first, original);
            last = previous;
        }
        return false;
    }

    private void perturb() {
        restarts++;
        int selected = random.nextInt(violated.cardinality());
        int row = violated.nextSetBit(0);
        while (selected-- > 0) row = violated.nextSetBit(row + 1);
        List<Integer> variables = new ArrayList<>();
        for (int id : rows.get(row).terms().keySet()) {
            charge();
            if (!lower[id].equals(upper[id])) variables.add(id);
        }
        if (variables.isEmpty()) return;
        int id = variables.get(random.nextInt(variables.size()));
        BigInteger a = rows.get(row).terms().get(id);
        BigInteger delta = floorDivide(residual[row].negate(), a);
        if (a.signum() < 0 && delta.multiply(a).compareTo(residual[row].negate()) > 0) delta = delta.add(BigInteger.ONE);
        BigInteger value = clamp(id, values[id].add(delta));
        if (value.equals(values[id])) {
            value = clamp(id, values[id].add(BigInteger.valueOf(random.nextBoolean() ? 1 : -1)));
        }
        if (!value.equals(values[id])) move(id, value);
    }

    private BigInteger clamp(int id, BigInteger value) {
        value = value.max(lower[id]);
        return upper[id] == null ? value : value.min(upper[id]);
    }

    private static BigInteger floorDivide(BigInteger a, BigInteger b) {
        BigInteger[] qr = a.divideAndRemainder(b);
        return qr[1].signum() != 0 && a.signum() != b.signum() ? qr[0].subtract(BigInteger.ONE) : qr[0];
    }

    private static double ratio(BigInteger a, BigInteger b) {
        if (a.signum() == 0) return 0;
        int left = Math.max(0, a.abs().bitLength() - 52), right = Math.max(0, b.bitLength() - 52);
        double value = a.shiftRight(left).doubleValue() / b.shiftRight(right).doubleValue();
        return Math.copySign(Math.max(1e-100, Math.min(1e100, Math.abs(Math.scalb(value, left - right)))), value);
    }

    private void charge() {
        budget.check();
        work++;
    }

    private boolean finish(String detail) {
        complete = true;
        budget.note("count_jump", detail + "; variables=" + values.length + "; rows=" + rows.size() +
                "; moves=" + moves + "; pairs=" + pairs + "; bumps=" + bumps + "; perturbations=" + restarts + "; work=" + work);
        return true;
    }

    BigInteger[] counts() {
        return counts == null ? null : counts.clone();
    }

    @Override
    public void close() {
        budget.release(memory);
        memory = 0;
    }
}
