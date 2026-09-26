package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Exact integer substitutions shared by DP, Boolean search and rational relaxation. */
final class CountReduction implements AutoCloseable {

    record Equality(int eliminated, int retained, int sign, BigInteger offset,
                    ExactLinearProgram.Constraint first, ExactLinearProgram.Constraint second) {}

    private final List<ExactLinearProgram.Constraint> source;
    private final PlanningBudget budget;
    private final BigInteger[] sourceLower, sourceUpper;
    private final int[] root, sign;
    private final BigInteger[] offset;
    private final List<Equality> proof = new ArrayList<>();
    private final Map<Map<Integer, BigInteger>, ExactLinearProgram.Constraint> known = new LinkedHashMap<>();
    private final int[] partners;
    private final BigInteger[] conserved;
    private BigInteger conservedUpper = BigInteger.ZERO;
    private List<ExactLinearProgram.Constraint> rows;
    private BigInteger[] lower, upper;
    private int[] representatives;
    private long memory, work, allowance;
    private int cursor, saturatedPairs;
    private boolean complete, changed, saturated;

    CountReduction(List<ExactLinearProgram.Constraint> source, BigInteger[] lower, BigInteger[] upper, PlanningBudget budget) {
        this.source = new ArrayList<>(source);
        this.sourceLower = lower.clone();
        this.sourceUpper = upper.clone();
        this.budget = budget;
        root = new int[lower.length];
        sign = new int[lower.length];
        offset = new BigInteger[lower.length];
        partners = new int[lower.length];
        Arrays.fill(partners, -1);
        conserved = new BigInteger[lower.length];
        Arrays.fill(conserved, BigInteger.ZERO);
        for (int i = 0; i < lower.length; i++) {
            root[i] = lower[i].equals(upper[i]) ? -1 : i;
            sign[i] = 1;
            offset[i] = root[i] < 0 ? lower[i] : BigInteger.ZERO;
            // Elimination must retain the domains of eliminated variables too.
            this.source.add(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE.negate()), lower[i].negate()));
            if (upper[i] != null) this.source.add(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE), upper[i]));
        }
        long entries = this.source.stream().mapToLong(row -> row.terms().size()).sum();
        long bytes = 1024 + 384L * lower.length + 192L * this.source.size() + 192L * entries;
        allowance = Math.min(131_072, budget.remainingWork() / 8);
        if (allowance < 1024 || !budget.tryReserve(bytes)) identity();
        else memory = bytes;
    }

    boolean step() {
        if (complete) return true;
        budget.check();
        if (work >= allowance) {
            // Keep all choices when compilation runs out of its local budget.
            identity();
            return true;
        }
        if (!saturated) {
            saturate();
            return false;
        }
        if (cursor == source.size()) {
            if (changed) {
                changed = false;
                known.clear();
                cursor = 0;
                return false;
            }
            finish();
            return true;
        }
        var row = normalize(project(source.get(cursor++)));
        if (row.terms().isEmpty() && row.upper().signum() >= 0) return false;
        var same = known.get(row.terms());
        if (same != null && same.upper().compareTo(row.upper()) <= 0) return false;
        known.put(row.terms(), row);
        if (row.terms().size() != 2) return false;
        Map<Integer, BigInteger> opposite = new HashMap<>();
        row.terms().forEach((key, value) -> opposite.put(key, value.negate()));
        var other = known.get(opposite);
        if (other == null || !row.upper().equals(other.upper().negate())) return false;
        var ids = row.terms().keySet().stream().sorted().toList();
        int keep = ids.get(0), remove = ids.get(1);
        BigInteger a = row.terms().get(remove), b = row.terms().get(keep);
        if (!a.abs().equals(b.abs()) || row.upper().remainder(a).signum() != 0) return false;
        int direction = b.negate().divide(a).intValueExact();
        BigInteger shift = row.upper().divide(a);
        proof.add(new Equality(remove, keep, direction, shift, row, other));
        for (int i = 0; i < root.length; i++) if (root[i] == remove) {
            charge();
            offset[i] = offset[i].add(shift.multiply(BigInteger.valueOf(sign[i])));
            sign[i] *= direction;
            root[i] = keep;
        }
        changed = true;
        return false;
    }

    /**
     * A nonnegative sum of necessary rows can saturate disjoint at-most-one
     * pairs. Each pair attaining a strictly negative minimum must select one
     * source. Export that equality to every downstream solver, without fixing
     * any trial count or assuming the other coupled rows are independent.
     */
    private void saturate() {
        if (cursor < source.size()) {
            var row = normalize(project(source.get(cursor++)));
            if (row.upper().signum() < 0 && row.terms().values().stream().allMatch(v -> v.signum() < 0)) {
                conservedUpper = conservedUpper.add(row.upper());
                for (var term : row.terms().entrySet()) {
                    charge();
                    int id = term.getKey();
                    conserved[id] = conserved[id].add(term.getValue());
                }
            }
            if (row.terms().size() != 2 || !row.upper().equals(BigInteger.ONE)) return;
            var ids = row.terms().keySet().iterator();
            int a = ids.next(), b = ids.next();
            if (partners[a] >= 0 || partners[b] >= 0 || !binary(a) || !binary(b) ||
                    !row.terms().get(a).equals(BigInteger.ONE) || !row.terms().get(b).equals(BigInteger.ONE))
                return;
            partners[a] = b;
            partners[b] = a;
            return;
        }
        saturated = true;
        cursor = 0;
        BigInteger minimum = BigInteger.ZERO;
        for (int i = 0; i < root.length; i++) {
            charge();
            if (root[i] < 0 || partners[i] >= 0 && partners[i] < i) continue;
            if (partners[i] >= 0) minimum = minimum.add(conserved[i].min(conserved[partners[i]]).min(BigInteger.ZERO));
            else if (conserved[i].signum() != 0) {
                if (sourceUpper[i] == null) return;
                minimum = minimum.add(conserved[i].multiply(sourceUpper[i]));
            }
        }
        if (!minimum.equals(conservedUpper)) return;
        for (int i = 0; i < root.length; i++) if (partners[i] > i && conserved[i].min(conserved[partners[i]]).signum() < 0) {
            charge();
            source.add(new ExactLinearProgram.Constraint(Map.of(i, BigInteger.ONE.negate(), partners[i], BigInteger.ONE.negate()),
                    BigInteger.ONE.negate()));
            saturatedPairs++;
        }
    }

    private boolean binary(int id) {
        return sourceLower[id].signum() == 0 && BigInteger.ONE.equals(sourceUpper[id]);
    }

    private ExactLinearProgram.Constraint project(ExactLinearProgram.Constraint row) {
        Map<Integer, BigInteger> terms = new LinkedHashMap<>();
        BigInteger rhs = row.upper();
        for (var term : row.terms().entrySet()) {
            charge();
            int id = term.getKey();
            rhs = rhs.subtract(term.getValue().multiply(offset[id]));
            if (root[id] >= 0) terms.merge(root[id], term.getValue().multiply(BigInteger.valueOf(sign[id])), BigInteger::add);
        }
        terms.values().removeIf(value -> value.signum() == 0);
        return new ExactLinearProgram.Constraint(terms, rhs);
    }

    static ExactLinearProgram.Constraint normalize(ExactLinearProgram.Constraint row) {
        if (row.terms().values().stream().anyMatch(value -> value.signum() == 0)) {
            Map<Integer, BigInteger> sparse = new LinkedHashMap<>(row.terms());
            sparse.values().removeIf(value -> value.signum() == 0);
            row = new ExactLinearProgram.Constraint(sparse, row.upper());
        }
        BigInteger gcd = BigInteger.ZERO;
        for (BigInteger value : row.terms().values()) gcd = gcd.gcd(value);
        if (gcd.compareTo(BigInteger.ONE) <= 0) return row;
        Map<Integer, BigInteger> terms = new LinkedHashMap<>();
        BigInteger divisor = gcd;
        row.terms().forEach((key, value) -> terms.put(key, value.divide(divisor)));
        BigInteger[] divided = row.upper().divideAndRemainder(divisor);
        return new ExactLinearProgram.Constraint(terms, divided[1].signum() < 0 ? divided[0].subtract(BigInteger.ONE) : divided[0]);
    }

    private void finish() {
        representatives = Arrays.stream(root).filter(id -> id >= 0).distinct().sorted().toArray();
        Map<Integer, Integer> ids = new HashMap<>();
        for (int i = 0; i < representatives.length; i++) ids.put(representatives[i], i);
        rows = new ArrayList<>();
        for (var row : known.values()) {
            Map<Integer, BigInteger> terms = new LinkedHashMap<>();
            row.terms().forEach((key, value) -> terms.put(ids.get(key), value));
            rows.add(new ExactLinearProgram.Constraint(terms, row.upper()));
        }
        lower = new BigInteger[representatives.length];
        upper = new BigInteger[representatives.length];
        Arrays.fill(lower, BigInteger.ZERO);
        for (int i = 0; i < root.length; i++) if (root[i] >= 0) {
            int id = ids.get(root[i]);
            BigInteger low = sign[i] > 0 ? sourceLower[i].subtract(offset[i]) :
                    sourceUpper[i] == null ? null : offset[i].subtract(sourceUpper[i]);
            BigInteger high = sign[i] > 0 ? sourceUpper[i] == null ? null : sourceUpper[i].subtract(offset[i]) : offset[i].subtract(sourceLower[i]);
            if (low != null) lower[id] = lower[id].max(low);
            if (high != null) upper[id] = upper[id] == null ? high : upper[id].min(high);
        }
        complete = true;
        budget.note("count_compile", "variables=" + root.length + "->" + representatives.length +
                "; rows=" + source.size() + "->" + rows.size() + "; equalities=" + proof.size() + "; saturated_pairs=" + saturatedPairs);
    }

    private void identity() {
        known.clear();
        proof.clear();
        for (int i = 0; i < root.length; i++) {
            root[i] = i;
            sign[i] = 1;
            offset[i] = BigInteger.ZERO;
        }
        representatives = root.clone();
        rows = List.copyOf(source);
        lower = sourceLower.clone();
        upper = sourceUpper.clone();
        complete = true;
    }

    BigInteger[] expand(BigInteger[] values) {
        if (values == null) return null;
        BigInteger[] result = offset.clone();
        for (int i = 0; i < result.length; i++) if (root[i] >= 0)
            result[i] = result[i].add(values[Arrays.binarySearch(representatives, root[i])].multiply(BigInteger.valueOf(sign[i])));
        return result;
    }

    ExactRational[] expand(ExactRational[] values) {
        if (values == null) return null;
        ExactRational[] result = new ExactRational[root.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = ExactRational.of(offset[i]);
            if (root[i] >= 0) result[i] = result[i].add(values[Arrays.binarySearch(representatives, root[i])].multiply(ExactRational.of(BigInteger.valueOf(sign[i]))));
        }
        return result;
    }

    BigInteger[] objective(BigInteger[] original) {
        BigInteger[] result = new BigInteger[representatives.length];
        Arrays.fill(result, BigInteger.ZERO);
        for (int i = 0; i < root.length; i++) if (root[i] >= 0) {
            int id = Arrays.binarySearch(representatives, root[i]);
            result[id] = result[id].add(original[i].multiply(BigInteger.valueOf(sign[i])));
        }
        return result;
    }

    /** Exact affine interval lower bound; correlations can only make it weaker. */
    BigInteger minimum(BigInteger[] coefficients) {
        Map<Integer, BigInteger> sparse = new LinkedHashMap<>();
        for (int i = 0; i < coefficients.length; i++) if (coefficients[i].signum() != 0) sparse.put(i, coefficients[i]);
        return minimum(sparse);
    }

    BigInteger minimum(Map<Integer, BigInteger> coefficients) {
        BigInteger result = BigInteger.ZERO;
        Map<Integer, BigInteger> projected = new HashMap<>();
        for (var term : coefficients.entrySet()) {
            charge();
            int i = term.getKey();
            result = result.add(term.getValue().multiply(offset[i]));
            if (root[i] >= 0) projected.merge(Arrays.binarySearch(representatives, root[i]), term.getValue().multiply(BigInteger.valueOf(sign[i])), BigInteger::add);
        }
        for (var term : projected.entrySet()) if (term.getValue().signum() != 0) {
            int i = term.getKey();
            BigInteger value = term.getValue().signum() > 0 ? lower[i] : upper[i];
            if (value == null) return null;
            result = result.add(term.getValue().multiply(value));
        }
        return result;
    }

    boolean sameCoordinates(CountReduction other) {
        return other != null && Arrays.equals(root, other.root) && Arrays.equals(sign, other.sign) && Arrays.equals(offset, other.offset);
    }

    record Coordinates(List<Integer> roots, List<Integer> signs, List<BigInteger> offsets) {}

    Coordinates coordinates() {
        return new Coordinates(Arrays.stream(root).boxed().toList(), Arrays.stream(sign).boxed().toList(), List.of(offset));
    }

    List<ExactLinearProgram.Constraint> rows() {
        return rows;
    }

    BigInteger[] lower() {
        return lower.clone();
    }

    BigInteger[] upper() {
        return upper.clone();
    }

    List<Equality> proof() {
        return List.copyOf(proof);
    }

    int variables() {
        return representatives.length;
    }

    int[] representatives() {
        return representatives.clone();
    }

    private void charge() {
        budget.check();
        work++;
    }

    @Override
    public void close() {
        budget.release(memory);
        memory = 0;
    }
}
