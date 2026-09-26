package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Exact two-row lattice repair around a relaxation. Only checked witnesses are exported. */
final class CountLatticeRepair implements AutoCloseable {

    private record Entry(int code, BigInteger a, BigInteger b) {}

    private final List<ExactLinearProgram.Constraint> rows;
    private final BigInteger[] lower, upper, base;
    private final PlanningBudget budget;
    private final Map<List<BigInteger>, List<Entry>> left = new HashMap<>();
    private final long allowance;
    private int[] free, sizes;
    private BigInteger[] a, b;
    private BigInteger determinant, rhsA, rhsB, sumA = BigInteger.ZERO, sumB = BigInteger.ZERO;
    private BigInteger[] counts;
    private Iterator<Entry> matches;
    private int first, second, split, leftStates, rightStates, index, phase;
    private long work, memory;
    private boolean complete;

    CountLatticeRepair(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper,
                       ExactRational[] point, int attempt, PlanningBudget budget) {
        this.rows = rows;
        this.lower = lower;
        this.upper = upper;
        this.budget = budget;
        allowance = Math.min(1_000_000, budget.remainingWork() / 8);
        base = new BigInteger[lower.length];
        if (lower.length > 64 || allowance < 1024) {
            complete = true;
            return;
        }
        for (int i = 0; i < base.length; i++) {
            if (upper[i] == null) {
                complete = true;
                return;
            }
            base[i] = point[i].floor().max(lower[i]).min(upper[i]);
        }
        var known = new HashMap<Map<Integer, BigInteger>, BigInteger>();
        for (var row : rows) known.merge(row.terms(), row.upper(), BigInteger::min);
        List<ExactLinearProgram.Constraint> equations = new ArrayList<>();
        Set<Map<Integer, BigInteger>> included = new HashSet<>();
        for (var row : rows) {
            if (row.terms().size() < 2 || included.contains(row.terms())) continue;
            Map<Integer, BigInteger> opposite = new HashMap<>();
            row.terms().forEach((id, value) -> opposite.put(id, value.negate()));
            if (!row.upper().negate().equals(known.get(opposite))) continue;
            equations.add(row);
            included.add(row.terms());
            included.add(opposite);
        }
        if (equations.size() < 2) {
            complete = true;
            return;
        }
        var x = equations.get(0);
        ExactLinearProgram.Constraint y = null;
        // Prefer the fractional basis coordinates, retaining all other bounds.
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < lower.length; i++) if (!lower[i].equals(upper[i])) candidates.add(i);
        candidates.sort(Comparator.comparing((Integer i) -> point[i].integral()));
        outer:
        for (int i : candidates) for (int j : candidates) if (i != j) for (int r = 1; r < equations.size(); r++) {
            charge();
            var next = equations.get(r);
            BigInteger det = coefficient(x, i).multiply(coefficient(next, j)).subtract(coefficient(x, j).multiply(coefficient(next, i)));
            if (det.signum() != 0) {
                first = i;
                second = j;
                determinant = det;
                y = next;
                break outer;
            }
        }
        if (y == null) {
            complete = true;
            return;
        }
        a = new BigInteger[lower.length];
        b = new BigInteger[lower.length];
        for (int i = 0; i < lower.length; i++) {
            a[i] = coefficient(x, i).multiply(coefficient(y, second)).subtract(coefficient(y, i).multiply(coefficient(x, second)));
            b[i] = coefficient(y, i).multiply(coefficient(x, first)).subtract(coefficient(x, i).multiply(coefficient(y, first)));
        }
        rhsA = x.upper().multiply(coefficient(y, second)).subtract(y.upper().multiply(coefficient(x, second)));
        rhsB = y.upper().multiply(coefficient(x, first)).subtract(x.upper().multiply(coefficient(y, first)));
        List<Integer> lhs = new ArrayList<>(), rhs = new ArrayList<>();
        Map<Integer, Integer> widths = new HashMap<>();
        long nl = 1, nr = 1;
        if (attempt > 0) Collections.rotate(candidates, attempt);
        for (int i : candidates) {
            if (i == first || i == second) continue;
            BigInteger start = lower[i], end = upper[i];
            if (attempt == 1 || attempt == 2) {
                BigInteger radius = BigInteger.valueOf(attempt == 1 ? 4 : 16);
                start = start.max(base[i].subtract(radius));
                end = end.min(base[i].add(radius));
            }
            BigInteger size = end.subtract(start).add(BigInteger.ONE);
            if (size.compareTo(BigInteger.valueOf(4096)) > 0) continue;
            int width = size.intValueExact();
            if (nl <= nr && nl * width <= 65536) {
                lhs.add(i);
                nl *= width;
                base[i] = start;
                widths.put(i, width);
            } else if (nr * width <= 65536) {
                rhs.add(i);
                nr *= width;
                base[i] = start;
                widths.put(i, width);
            }
        }
        split = lhs.size();
        lhs.addAll(rhs);
        free = lhs.stream().mapToInt(Integer::intValue).toArray();
        sizes = Arrays.stream(free).map(widths::get).toArray();
        leftStates = (int) nl;
        rightStates = (int) nr;
        for (int i = 0; i < base.length; i++) if (i != first && i != second) {
            rhsA = rhsA.subtract(a[i].multiply(base[i]));
            rhsB = rhsB.subtract(b[i].multiply(base[i]));
        }
        long bytes = 2048L + 256L * leftStates;
        if (!budget.tryReserve(bytes)) complete = true;
        else memory = bytes;
    }

    boolean step() {
        if (complete) return true;
        charge();
        if (work >= allowance) return finish("work_limit");
        if (phase == 0) {
            left.computeIfAbsent(key(sumA, sumB), unused -> new ArrayList<>()).add(new Entry(index, sumA, sumB));
            if (++index == leftStates) {
                index = 0;
                sumA = sumB = BigInteger.ZERO;
                phase = 1;
            } else advance(index - 1, 0, split);
            return false;
        }
        if (matches == null) matches = left.getOrDefault(key(rhsA.subtract(sumA), rhsB.subtract(sumB)), List.of()).iterator();
        if (matches.hasNext()) {
            Entry entry = matches.next();
            BigInteger[] u = rhsA.subtract(sumA).subtract(entry.a()).divideAndRemainder(determinant);
            BigInteger[] v = rhsB.subtract(sumB).subtract(entry.b()).divideAndRemainder(determinant);
            if (u[1].signum() != 0 || v[1].signum() != 0) throw new IllegalStateException("Invalid lattice residue");
            if (u[0].compareTo(lower[first]) < 0 || u[0].compareTo(upper[first]) > 0 || v[0].compareTo(lower[second]) < 0 || v[0].compareTo(upper[second]) > 0) return false;
            BigInteger[] candidate = base.clone();
            candidate[first] = u[0];
            candidate[second] = v[0];
            decode(candidate, entry.code(), 0, split);
            decode(candidate, index, split, free.length);
            for (var row : rows) {
                BigInteger sum = BigInteger.ZERO;
                for (var term : row.terms().entrySet()) {
                    charge();
                    sum = sum.add(term.getValue().multiply(candidate[term.getKey()]));
                }
                if (sum.compareTo(row.upper()) > 0) return false;
            }
            counts = candidate;
            return finish("verified_witness");
        }
        matches = null;
        if (++index == rightStates) return finish("face_unresolved");
        advance(index - 1, split, free.length);
        return false;
    }

    private static BigInteger coefficient(ExactLinearProgram.Constraint row, int id) {
        return row.terms().getOrDefault(id, BigInteger.ZERO);
    }

    private List<BigInteger> key(BigInteger x, BigInteger y) {
        return List.of(x.mod(determinant.abs()), y.mod(determinant.abs()));
    }

    private void advance(int previous, int start, int end) {
        for (int i = start; i < end; i++) {
            charge();
            int digit = previous % sizes[i];
            previous /= sizes[i];
            int delta = digit + 1 == sizes[i] ? -digit : 1;
            sumA = sumA.add(a[free[i]].multiply(BigInteger.valueOf(delta)));
            sumB = sumB.add(b[free[i]].multiply(BigInteger.valueOf(delta)));
            if (delta == 1) break;
        }
    }

    private void decode(BigInteger[] values, int code, int start, int end) {
        for (int i = start; i < end; i++) {
            values[free[i]] = base[free[i]].add(BigInteger.valueOf(code % sizes[i]));
            code /= sizes[i];
        }
    }

    private void charge() {
        budget.check();
        work++;
    }

    private boolean finish(String detail) {
        complete = true;
        budget.note("count_lattice", detail + "; free=" + free.length + "; left_states=" + leftStates + "; right_states=" + rightStates + "; work=" + work);
        return true;
    }

    BigInteger[] counts() {
        return counts == null ? null : counts.clone();
    }

    @Override
    public void close() {
        left.clear();
        budget.release(memory);
        memory = 0;
    }
}
