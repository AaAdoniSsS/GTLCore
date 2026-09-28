package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Hall filtering of an exact-one / unit-capacity relaxation, with row-sum proofs. */
final class CountHall implements AutoCloseable {

    private record Edge(int variable, int resource) {}

    private final PlanningBudget budget;
    private final List<ExactLinearProgram.Constraint> rows;
    private final BigInteger[] lower, upper;
    private final List<ExactLinearProgram.Constraint> cuts = new ArrayList<>();
    private final List<Integer> demands = new ArrayList<>(), capacities = new ArrayList<>();
    private final List<List<Edge>> graph = new ArrayList<>();
    private final List<CountProof.Row> axioms = new ArrayList<>();
    private int[] owner, resource, matched;
    private final long allowance;
    private long work, memory;
    private int group, edge, attempts;
    private boolean complete, initialized;

    CountHall(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper, PlanningBudget budget) {
        this.rows = rows;
        this.lower = lower;
        this.upper = upper;
        this.budget = budget;
        allowance = Math.min(32768, budget.remainingWork() / 64);
        if (lower.length > 512 || rows.size() > 2048 || allowance < 4096) {
            complete = true;
            return;
        }
        for (int j = 0; j < lower.length; j++) if (lower[j].signum() < 0 || upper[j] == null || upper[j].compareTo(BigInteger.ONE) > 0) {
            complete = true;
            return;
        }
        long bytes = 4096L + 768L * lower.length + 128L * rows.size();
        if (!budget.tryReserve(bytes)) {
            complete = true;
            return;
        }
        memory = bytes;
    }

    boolean step() {
        if (complete) return true;
        if (work >= allowance || cuts.size() >= 16) return finish();
        if (!initialized) {
            initialized = true;
            prepare();
            return complete;
        }
        while (group < graph.size() && edge == graph.get(group).size()) {
            group++;
            edge = 0;
        }
        if (group == graph.size()) return finish();
        var choice = graph.get(group).get(edge++);
        if (upper[choice.variable()].signum() == 0) return false;
        // Other constraints and fixed-one assignments are deliberately relaxed.
        // Failing this larger matching problem still proves the edge impossible.
        Arrays.fill(matched, -1);
        BitSet left = new BitSet(), right = new BitSet();
        for (int g = 0; g < graph.size(); g++) if (g != group) {
            left.clear();
            right.clear();
            if (!augment(g, choice.resource(), left, right)) {
                certify(choice, left, right);
                break;
            }
            if (work >= allowance) return finish();
        }
        attempts++;
        return false;
    }

    private void prepare() {
        var indices = new HashMap<ExactLinearProgram.Constraint, Integer>();
        for (int r = 0; r < rows.size(); r++) {
            charge();
            indices.put(rows.get(r), r);
            axioms.add(CountProof.row(rows.get(r)));
        }
        owner = new int[lower.length];
        resource = new int[lower.length];
        Arrays.fill(owner, -1);
        Arrays.fill(resource, -1);
        for (int r = 0; r < rows.size(); r++) {
            charge();
            var row = rows.get(r);
            if (!row.upper().equals(BigInteger.ONE.negate()) || row.terms().size() < 2 ||
                    row.terms().entrySet().stream().anyMatch(e -> !e.getValue().equals(BigInteger.ONE.negate()) || owner[e.getKey()] >= 0))
                continue;
            var opposite = new TreeMap<Integer, BigInteger>();
            for (int id : row.terms().keySet()) opposite.put(id, BigInteger.ONE);
            if (!indices.containsKey(new ExactLinearProgram.Constraint(opposite, BigInteger.ONE))) continue;
            int id = demands.size();
            demands.add(r);
            graph.add(new ArrayList<>());
            row.terms().keySet().forEach(j -> owner[j] = id);
        }
        if (demands.size() < 2 || demands.size() > 128) {
            finish();
            return;
        }
        for (int r = 0; r < rows.size(); r++) {
            charge();
            var row = rows.get(r);
            if (!row.upper().equals(BigInteger.ONE) || row.terms().isEmpty() ||
                    row.terms().values().stream().anyMatch(c -> !c.equals(BigInteger.ONE)))
                continue;
            int distinct = -1;
            boolean useful = false, overlaps = false;
            for (int j : row.terms().keySet()) {
                charge();
                if (owner[j] < 0) continue;
                if (resource[j] >= 0) overlaps = true;
                if (distinct < 0) distinct = owner[j];
                else if (distinct != owner[j]) useful = true;
            }
            if (!useful || overlaps) continue;
            int id = capacities.size();
            capacities.add(r);
            row.terms().keySet().forEach(j -> { if (owner[j] >= 0) resource[j] = id; });
        }
        for (int j = 0; j < owner.length; j++) if (owner[j] >= 0) {
            charge();
            if (resource[j] < 0) {
                resource[j] = capacities.size();
                capacities.add(axioms.size());
                axioms.add(new CountProof.Row(Map.of(j, BigInteger.ONE), BigInteger.ONE));
            }
            if (upper[j].signum() != 0) graph.get(owner[j]).add(new Edge(j, resource[j]));
        }
        // Explicit domain axioms also justify dropping outside positive terms.
        for (int j = 0; j < lower.length; j++) {
            axioms.add(new CountProof.Row(Map.of(j, BigInteger.ONE.negate()), BigInteger.ZERO));
            if (upper[j].signum() == 0) axioms.add(new CountProof.Row(Map.of(j, BigInteger.ONE), BigInteger.ZERO));
        }
        matched = new int[capacities.size()];
    }

    private boolean augment(int g, int forbidden, BitSet left, BitSet right) {
        charge();
        if (left.get(g)) return false;
        left.set(g);
        for (var e : graph.get(g)) {
            charge();
            int r = e.resource();
            if (r == forbidden || right.get(r)) continue;
            right.set(r);
            if (matched[r] < 0 || augment(matched[r], forbidden, left, right)) {
                matched[r] = g;
                return true;
            }
        }
        return false;
    }

    private void certify(Edge choice, BitSet left, BitSet right) {
        var parents = new TreeMap<Integer, BigInteger>();
        for (int g = left.nextSetBit(0); g >= 0; g = left.nextSetBit(g + 1)) parents.put(demands.get(g), BigInteger.ONE);
        right.set(choice.resource());
        for (int r = right.nextSetBit(0); r >= 0; r = right.nextSetBit(r + 1)) parents.put(capacities.get(r), BigInteger.ONE);
        var terms = new TreeMap<Integer, BigInteger>();
        BigInteger bound = BigInteger.ZERO;
        for (int index : parents.keySet()) {
            var row = axioms.get(index);
            bound = bound.add(row.upper());
            for (var e : row.terms().entrySet()) {
                charge();
                terms.merge(e.getKey(), e.getValue(), BigInteger::add);
            }
        }
        // Deleted zero-domain edges may appear with a negative coefficient;
        // their explicit x <= 0 axiom cancels them. Positive outside terms use x >= 0.
        for (var term : terms.entrySet()) if (term.getKey() != choice.variable() && term.getValue().signum() != 0) {
            int j = term.getKey();
            BigInteger coefficient = term.getValue();
            if (coefficient.signum() < 0 && upper[j].signum() != 0) return;
            var axiom = new CountProof.Row(Map.of(j, coefficient.signum() > 0 ? BigInteger.ONE.negate() : BigInteger.ONE), BigInteger.ZERO);
            int index = axioms.indexOf(axiom);
            if (index < 0) return;
            parents.merge(index, coefficient.abs(), BigInteger::add);
        }
        if (!BigInteger.ONE.equals(terms.get(choice.variable())) || bound.signum() > 0) return;
        var cut = new ExactLinearProgram.Constraint(Map.of(choice.variable(), BigInteger.ONE), bound);
        if (cuts.contains(cut)) return;
        var proof = new CountProof.Derivation("hall_forced_edge", lower.length, axioms,
                List.of(new CountProof.Combination(parents, BigInteger.ONE, CountProof.row(cut))));
        long checks = 512L + axioms.size() + parents.keySet().stream().mapToLong(i -> 4L + axioms.get(i).terms().size()).sum();
        if (work + checks > allowance) return;
        budget.charge(checks);
        work += checks;
        if (CountProof.verify(proof, checks) != CountProof.Verdict.VERIFIED) return;
        cuts.add(cut);
        if (budget.proofJournal() != null) budget.proofJournal().add(proof);
    }

    private void charge() {
        budget.check();
        work++;
    }

    private boolean finish() {
        complete = true;
        if (!demands.isEmpty()) budget.note("count_hall", "groups=" + demands.size() + "; attempts=" + attempts + "; cuts=" + cuts.size() + "; work=" + work);
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
