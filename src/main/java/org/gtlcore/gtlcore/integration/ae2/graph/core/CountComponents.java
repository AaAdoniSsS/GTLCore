package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Exact quantity-only factorization; lifted counts still require global scheduling. */
final class CountComponents implements AutoCloseable {

    private static final class LocalLimit extends RuntimeException {

        LocalLimit() {
            super(null, null, false, false);
        }
    }

    private record Component(int[] variables, List<ExactLinearProgram.Constraint> rows,
                             BigInteger[] lower, BigInteger[] upper) {}

    private final List<ExactLinearProgram.Constraint> original;
    private final BigInteger[] lower, upper;
    private final PlanningBudget budget;
    private final List<List<Integer>> groups = new ArrayList<>();
    private final List<Component> components = new ArrayList<>();
    private BigInteger[] widths, totals, counts;
    private BitSet resolved = new BitSet();
    private CountPartition partition;
    private CountMeetInMiddle matching;
    private CountBoolean binary;
    private CountQuickSolve polishing;
    private int cursor;
    private long memory, work, allowance;
    private boolean prepared, complete, infeasible, unresolved;

    CountComponents(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper, PlanningBudget budget) {
        original = rows;
        this.lower = lower.clone();
        this.upper = upper.clone();
        this.budget = budget;
        long entries = rows.stream().mapToLong(row -> row.terms().size()).sum();
        allowance = Math.min(524_288, budget.remainingWork() / 8);
        long bytes = 2048 + 768L * lower.length + 256L * rows.size() + 384L * entries;
        if (allowance < 1024 || !budget.tryReserve(bytes)) complete = true;
        else memory = bytes;
    }

    boolean step() {
        if (complete) return true;
        budget.check();
        if (!prepared) {
            prepared = true;
            try {
                prepare();
            } catch (LocalLimit limit) {
                complete = true;
            }
            return complete;
        }
        if (partition != null) {
            if (!partition.step()) return false;
            var found = partition.counts();
            partition.close();
            partition = null;
            if (found != null) accept(found, false);
            else {
                var component = components.get(cursor);
                boolean weighted = component.rows.stream().flatMap(row -> row.terms().values().stream())
                        .anyMatch(value -> value.abs().compareTo(BigInteger.ONE) > 0);
                if (weighted) matching = new CountMeetInMiddle(component.rows, component.lower, component.upper, budget);
                else binary = new CountBoolean(component.rows, component.lower, component.upper, budget);
            }
            return complete;
        }
        if (matching != null) {
            if (!matching.step()) return false;
            var found = matching.counts();
            boolean impossible = matching.infeasible();
            matching.close();
            matching = null;
            if (found != null || impossible) accept(found, impossible);
            else {
                var component = components.get(cursor);
                binary = new CountBoolean(component.rows, component.lower, component.upper, budget);
            }
            return complete;
        }
        if (binary != null) {
            if (!binary.step()) return false;
            var found = binary.counts();
            boolean impossible = binary.infeasible();
            binary.close();
            binary = null;
            if (found != null || impossible) accept(found, impossible);
            else {
                var component = components.get(cursor);
                polishing = new CountQuickSolve(component.rows, component.lower, component.upper, budget, false, false);
            }
            return complete;
        }
        if (polishing != null) {
            if (!polishing.step()) return false;
            var found = polishing.counts();
            boolean impossible = polishing.infeasible();
            polishing.close();
            polishing = null;
            accept(found, impossible);
            return complete;
        }
        if (cursor < components.size()) {
            var component = components.get(cursor);
            partition = new CountPartition(component.rows, component.lower, component.upper, budget);
            return false;
        }
        complete = true;
        if (unresolved) return true;
        // A sum of contiguous integer intervals has no holes. Distribute every
        // aggregate within its original domains, then check ALL original rows.
        var lifted = lower.clone();
        for (int group = 0; group < groups.size(); group++) {
            BigInteger left = totals[group];
            for (int id : groups.get(group)) {
                budget.check();
                BigInteger part = widths[id] == null ? left : left.min(widths[id]);
                lifted[id] = lifted[id].add(part);
                left = left.subtract(part);
            }
            if (left.signum() != 0) throw new IllegalStateException("Undistributable count aggregate");
        }
        if (!valid(lifted)) throw new IllegalStateException("Invalid lifted component counts");
        counts = lifted;
        budget.note("count_components", "witness; components=" + components.size() + "; lifted_variables=" + counts.length);
        return true;
    }

    private void prepare() {
        // Absorb unary rows before comparing columns: bounds are private to
        // each variable, whereas every other distinguishing row is retained.
        for (var row : original) if (row.terms().size() == 1) {
            charge();
            var term = row.terms().entrySet().iterator().next();
            int id = term.getKey();
            BigInteger a = term.getValue();
            if (a.signum() > 0) {
                BigInteger value = floor(row.upper(), a);
                upper[id] = upper[id] == null ? value : upper[id].min(value);
            } else if (a.signum() < 0) lower[id] = lower[id].max(floor(row.upper(), a.negate()).negate());
        }
        widths = new BigInteger[lower.length];
        for (int i = 0; i < lower.length; i++) {
            charge();
            if (upper[i] != null) {
                widths[i] = upper[i].subtract(lower[i]);
                if (widths[i].signum() < 0) {
                    fail();
                    return;
                }
            }
        }
        List<ExactLinearProgram.Constraint> rows = new ArrayList<>();
        for (var row : original) {
            Map<Integer, BigInteger> terms = new LinkedHashMap<>();
            BigInteger bound = row.upper(), minimum = BigInteger.ZERO, maximum = BigInteger.ZERO;
            for (var term : row.terms().entrySet()) {
                charge();
                int id = term.getKey();
                BigInteger coefficient = term.getValue();
                bound = bound.subtract(coefficient.multiply(lower[id]));
                if (BigInteger.ZERO.equals(widths[id]) || coefficient.signum() == 0) continue;
                terms.put(id, coefficient);
                if (coefficient.signum() > 0) maximum = maximum == null || widths[id] == null ? null : maximum.add(coefficient.multiply(widths[id]));
                else minimum = minimum == null || widths[id] == null ? null : minimum.add(coefficient.multiply(widths[id]));
            }
            if (minimum != null && minimum.compareTo(bound) > 0) {
                fail();
                return;
            }
            if (maximum != null && maximum.compareTo(bound) <= 0) continue;
            rows.add(new ExactLinearProgram.Constraint(terms, bound));
        }
        var columns = new ArrayList<Map<Integer, BigInteger>>();
        for (int i = 0; i < lower.length; i++) columns.add(new LinkedHashMap<>());
        for (int r = 0; r < rows.size(); r++) for (var term : rows.get(r).terms().entrySet()) {
            charge();
            columns.get(term.getKey()).put(r, term.getValue());
        }
        Map<Map<Integer, BigInteger>, Integer> identical = new LinkedHashMap<>();
        int[] groupIds = new int[lower.length];
        Arrays.fill(groupIds, -1);
        int live = 0;
        for (int i = 0; i < lower.length; i++) {
            charge();
            if (columns.get(i).isEmpty()) continue;
            live++;
            Integer group = identical.get(columns.get(i));
            if (group == null) {
                group = groups.size();
                identical.put(columns.get(i), group);
                groups.add(new ArrayList<>());
            }
            groups.get(group).add(i);
            groupIds[i] = group;
        }
        BigInteger[] high = new BigInteger[groups.size()];
        for (int g = 0; g < groups.size(); g++) {
            high[g] = BigInteger.ZERO;
            for (int id : groups.get(g)) {
                charge();
                if (widths[id] == null) {
                    high[g] = null;
                    break;
                }
                high[g] = high[g].add(widths[id]);
            }
        }
        int[] parent = new int[groups.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        var aggregated = new ArrayList<ExactLinearProgram.Constraint>();
        for (var row : rows) {
            Map<Integer, BigInteger> terms = new LinkedHashMap<>();
            int first = -1;
            for (var term : row.terms().entrySet()) {
                charge();
                int g = groupIds[term.getKey()];
                // Equal columns multiply their SUM, not sum * group size.
                terms.put(g, term.getValue());
                if (first < 0) first = g;
                else parent[root(parent, g)] = root(parent, first);
            }
            aggregated.add(new ExactLinearProgram.Constraint(terms, row.upper()));
        }
        Map<Integer, List<Integer>> parts = new LinkedHashMap<>();
        for (int i = 0; i < parent.length; i++) parts.computeIfAbsent(root(parent, i), unused -> new ArrayList<>()).add(i);
        if (parts.size() <= 1 && groups.size() == live) {
            complete = true;
            return;
        }
        for (var part : parts.values()) {
            Map<Integer, Integer> ids = new HashMap<>();
            for (int i = 0; i < part.size(); i++) ids.put(part.get(i), i);
            List<ExactLinearProgram.Constraint> local = new ArrayList<>();
            for (var row : aggregated) {
                charge();
                if (!ids.containsKey(row.terms().keySet().iterator().next())) continue;
                Map<Integer, BigInteger> terms = new LinkedHashMap<>();
                row.terms().forEach((id, value) -> terms.put(ids.get(id), value));
                local.add(new ExactLinearProgram.Constraint(terms, row.upper()));
            }
            BigInteger[] low = new BigInteger[part.size()], up = new BigInteger[part.size()];
            Arrays.fill(low, BigInteger.ZERO);
            for (int i = 0; i < up.length; i++) up[i] = high[part.get(i)];
            components.add(new Component(part.stream().mapToInt(i -> i).toArray(), local, low, up));
        }
        totals = new BigInteger[groups.size()];
        Arrays.fill(totals, BigInteger.ZERO);
        budget.note("count_components", "variables=" + live + "->" + groups.size() + "; component_sizes=" + parts.values().stream().map(List::size).toList());
    }

    private void accept(BigInteger[] found, boolean impossible) {
        if (impossible) {
            fail();
            return;
        }
        if (found == null) unresolved = true;
        else {
            var component = components.get(cursor);
            for (int i = 0; i < found.length; i++) {
                totals[component.variables[i]] = found[i];
                resolved.set(component.variables[i]);
            }
        }
        cursor++;
    }

    private boolean valid(BigInteger[] values) {
        for (int i = 0; i < values.length; i++) if (values[i].compareTo(lower[i]) < 0 || upper[i] != null && values[i].compareTo(upper[i]) > 0) return false;
        for (var row : original) {
            BigInteger sum = BigInteger.ZERO;
            for (var term : row.terms().entrySet()) {
                budget.check();
                sum = sum.add(term.getValue().multiply(values[term.getKey()]));
            }
            if (sum.compareTo(row.upper()) > 0) return false;
        }
        return true;
    }

    private static int root(int[] parent, int id) {
        while (id != parent[id]) {
            parent[id] = parent[parent[id]];
            id = parent[id];
        }
        return id;
    }

    private static BigInteger floor(BigInteger a, BigInteger b) {
        var divided = a.divideAndRemainder(b);
        return divided[1].signum() < 0 ? divided[0].subtract(BigInteger.ONE) : divided[0];
    }

    private void charge() {
        budget.check();
        if (++work > allowance) throw new LocalLimit();
    }

    private void fail() {
        complete = infeasible = true;
        budget.note("count_components", "proven_infeasible; component=" + cursor);
    }

    BigInteger[] counts() {
        return counts;
    }

    boolean infeasible() {
        return infeasible;
    }

    /** Solved independent components can seed a later candidate, never a global no-good. */
    BigInteger[] partial() {
        if (resolved.isEmpty()) return null;
        BigInteger[] result = new BigInteger[lower.length];
        for (int group = resolved.nextSetBit(0); group >= 0; group = resolved.nextSetBit(group + 1)) {
            BigInteger left = totals[group];
            for (int id : groups.get(group)) {
                BigInteger part = widths[id] == null ? left : left.min(widths[id]);
                result[id] = lower[id].add(part);
                left = left.subtract(part);
            }
        }
        return result;
    }

    @Override
    public void close() {
        if (partition != null) partition.close();
        if (matching != null) matching.close();
        if (binary != null) binary.close();
        if (polishing != null) polishing.close();
        budget.release(memory);
        memory = 0;
    }
}
