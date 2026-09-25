package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/**
 * Native integer state-equation search with executable witnesses. Branch on
 * fractional counts; refine spurious integer candidates only with a proved
 * support obstruction or an exhaustively rejected fixed multiset.
 */
final class IntegerCountSearch<K> implements AutoCloseable {

    private final RecipeCountModel<K> model;
    private final K target;
    private final long amount, started;
    private final Map<K, Long> stock, seeds;
    private final Set<K> external;
    private final boolean preserve, force;
    private final PlanningBudget budget;
    private final long allowance;
    private final Deque<List<ExactLinearProgram.Constraint>> pending = new ArrayDeque<>();
    private List<ExactLinearProgram.Constraint> current;
    private List<ExactLinearProgram.Constraint> linearConstraints;
    private final Set<ExactLinearProgram.Constraint> materialConflicts = new LinkedHashSet<>();
    private final List<SupportConflict> supportConflicts = new ArrayList<>();
    private final Set<List<BigInteger>> rejectedMultisets = new HashSet<>();
    private CountBounds propagating;
    private int globalRows;
    private ExactLinearProgram linear;
    private CountSchedule<K> scheduling;
    private AllocationSearch.Candidate<K> assembling;
    private BigInteger[] counts;
    private GraphPlan<K> result;
    private boolean complete, infeasible;
    private long memory, work;
    private int candidates;

    IntegerCountSearch(GraphCompiler<K> compiler, K target, long amount, Map<K, Long> stock,
                       Map<K, Long> seeds, Set<K> external, Set<String> excluded, boolean preserve, boolean force,
                       PlanningBudget budget, long started) {
        this.target = target;
        this.amount = amount;
        this.stock = stock;
        this.seeds = seeds;
        this.external = external;
        this.preserve = preserve;
        this.force = force;
        this.budget = budget;
        allowance = Math.min(2_000_000, budget.remainingWork() / 4);
        this.started = started;
        model = RecipeCountModel.create(compiler, target, amount, stock, seeds, external, excluded, force, budget);
        if (model == null || model.recipes.size() > 64 || model.keys.size() > 96) {
            if (model != null) budget.note("integer_counts", "skipped; recipes=" + model.recipes.size() + "/64; keys=" + model.keys.size() + "/96");
            complete = true;
            close();
            return;
        }
        if (!budget.tryReserve(2L << 20)) {
            budget.note("integer_counts", "skipped; workspace_bytes=" + (2L << 20) + "; insufficient memory");
            complete = true;
            close();
            return;
        }
        memory = 2L << 20;
        pending.add(List.of());
    }

    boolean step() {
        long before = budget.nodes();
        try {
            return advance();
        } catch (ExactRational.PrecisionLimit limit) {
            return finish(false);
        } finally {
            work += budget.nodes() - before;
        }
    }

    private boolean advance() {
        budget.check();
        if (complete) return true;
        if (work >= allowance || candidates >= 128 || pending.size() > 256) {
            budget.note("integer_counts", "local_limit; work=" + work + "/" + allowance + "; candidates=" + candidates + "/128; frontier=" + pending.size() + "/256");
            return finish(false);
        }
        if (assembling != null) {
            if (!assembling.step()) return false;
            result = assembling.plan;
            if (result != null) {
                try {
                    PlanVerifier.verifyRuntimeInventory(result);
                } catch (ArithmeticException capacity) {
                    // These counts might still admit a streaming/nested order.
                    // Reject this buffered witness, never learn an impossibility.
                    result = null;
                }
            }
            return finish(false);
        }
        if (scheduling != null) {
            if (!scheduling.step()) return false;
            if (scheduling.result() == CountSchedule.Result.WITNESS) {
                Map<String, GraphRecipe<K>> recipes = new LinkedHashMap<>();
                model.recipes.forEach(recipe -> recipes.put(recipe.id(), recipe));
                assembling = new AllocationSearch.Candidate<>(scheduling.witness(), recipes, target, amount, stock, seeds, external,
                        preserve, force, false, budget, started);
                scheduling.close();
                scheduling = null;
                return false;
            }
            if (scheduling.result() != CountSchedule.Result.DEAD) return finish(false);
            scheduling.close();
            scheduling = null;
            excludeProvedMultiset();
            return false;
        }
        if (propagating != null) {
            if (!propagating.step()) return false;
            boolean blocked = propagating.blocked();
            var bounds = propagating.tightened();
            propagating.close();
            propagating = null;
            if (blocked) return false;
            linearConstraints.addAll(bounds);
            linear = new ExactLinearProgram(model.recipes.size(), linearConstraints, model.objective(true), budget);
            return false;
        }
        if (linear == null) {
            if (pending.isEmpty()) return finish(true);
            current = pending.removeLast();
            linearConstraints = new ArrayList<>(model.constraints);
            linearConstraints.addAll(materialConflicts);
            globalRows = linearConstraints.size();
            linearConstraints.addAll(current);
            propagating = new CountBounds(model.recipes.size(), linearConstraints, budget);
            candidates++;
            return false;
        }
        if (!linear.step()) return false;
        var status = linear.result();
        ExactRational[] point = linear.point();
        if (status == ExactLinearProgram.Result.INFEASIBLE) learnMaterialConflict(linear.certificate());
        linear.close();
        linear = null;
        if (status == ExactLinearProgram.Result.INFEASIBLE) return false;
        if (status != ExactLinearProgram.Result.OPTIMAL) return finish(false);
        for (SupportConflict conflict : supportConflicts) if (violates(conflict, point)) {
            branch(conflict);
            return false;
        }
        for (int i = 0; i < point.length; i++) if (!point[i].integral()) {
            enqueue(current, bound(i, point[i].floor(), false));
            enqueue(current, bound(i, point[i].ceil(), true));
            return false;
        }
        counts = Arrays.stream(point).map(ExactRational::numerator).toArray(BigInteger[]::new);
        if (rejectedMultisets.contains(List.of(counts))) {
            excludeProvedMultiset();
            return false;
        }
        if (refineSupport()) return false;
        scheduling = new CountSchedule<>(model, counts, budget);
        return false;
    }

    private boolean refineSupport() {
        Set<K> reachable = new HashSet<>(external);
        stock.forEach((key, value) -> { if (value > 0) reachable.add(key); });
        BitSet fired = new BitSet();
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < counts.length; i++) {
                budget.check();
                if (counts[i].signum() == 0 || fired.get(i)) continue;
                GraphRecipe<K> recipe = model.recipes.get(i);
                if (consumedKeys(recipe).stream().anyMatch(key -> !reachable.contains(key))) continue;
                reachable.addAll(recipe.outputs().keySet());
                fired.set(i);
                changed = true;
            }
        } while (changed);
        for (int blocked = 0; blocked < counts.length; blocked++) if (counts[blocked].signum() > 0 && !fired.get(blocked)) {
            // If this transition is used, a first producer must introduce an
            // initially absent place without itself requiring an absent place.
            // Keep both possibilities: avoid the transition, or include a repair.
            Map<Integer, BigInteger> repairs = new LinkedHashMap<>();
            for (int i = 0; i < counts.length; i++) {
                budget.check();
                GraphRecipe<K> recipe = model.recipes.get(i);
                if (consumedKeys(recipe).stream().allMatch(reachable::contains) &&
                        recipe.outputs().keySet().stream().anyMatch(key -> model.ids.containsKey(key) && !reachable.contains(key)))
                    repairs.put(i, BigInteger.ONE.negate());
            }
            SupportConflict conflict = new SupportConflict(blocked, new ExactLinearProgram.Constraint(repairs, BigInteger.ONE.negate()));
            if (!supportConflicts.contains(conflict)) supportConflicts.add(conflict);
            branch(conflict);
            return true;
        }
        return false;
    }

    private Set<K> consumedKeys(GraphRecipe<K> recipe) {
        Set<K> keys = new LinkedHashSet<>(recipe.inputs().keySet());
        keys.removeIf(key -> recipe.inputs().get(key).longValue() == recipe.configurationInputs().getOrDefault(key, 0L));
        return keys;
    }

    private void excludeProvedMultiset() {
        // Disjoint lexicographic branches cover every OTHER integer vector.
        // This is only reached after complete finite scheduling, never a timeout.
        rejectedMultisets.add(List.of(counts.clone()));
        var prefix = new ArrayList<>(current);
        for (int i = 0; i < counts.length; i++) {
            if (counts[i].signum() > 0) enqueue(prefix, bound(i, counts[i].subtract(BigInteger.ONE), false));
            enqueue(prefix, bound(i, counts[i].add(BigInteger.ONE), true));
            prefix.add(bound(i, counts[i], false));
            prefix.add(bound(i, counts[i], true));
        }
    }

    private record SupportConflict(int blocked, ExactLinearProgram.Constraint repair) {}

    private boolean violates(SupportConflict conflict, ExactRational[] point) {
        if (point[conflict.blocked()].signum() == 0) return false;
        ExactRational repairs = ExactRational.ZERO;
        for (int variable : conflict.repair().terms().keySet()) repairs = repairs.add(point[variable]);
        return repairs.compareTo(ExactRational.ONE) < 0;
    }

    private void branch(SupportConflict conflict) {
        enqueue(current, bound(conflict.blocked(), BigInteger.ZERO, false));
        if (!conflict.repair().terms().isEmpty()) {
            var required = new ArrayList<>(current);
            required.add(bound(conflict.blocked(), BigInteger.ONE, true));
            enqueue(required, conflict.repair());
        }
    }

    private void learnMaterialConflict(ExactRational[] proof) {
        if (materialConflicts.size() >= 64) return;
        // Combine only globally valid material rows. The other Farkas terms
        // describe this branch; dropping those terms leaves a reusable resource
        // inequality that is valid for all branches of THIS inventory snapshot.
        BigInteger scale = BigInteger.ONE;
        for (int i = 0; i < globalRows; i++) {
            scale = scale.divide(scale.gcd(proof[i].denominator())).multiply(proof[i].denominator());
            if (scale.bitLength() > 2048) return;
        }
        Map<Integer, BigInteger> terms = new LinkedHashMap<>();
        BigInteger upper = BigInteger.ZERO;
        for (int i = 0; i < globalRows; i++) {
            BigInteger weight = proof[i].numerator().multiply(scale.divide(proof[i].denominator()));
            if (weight.signum() < 0) return;
            if (weight.signum() == 0) continue;
            upper = upper.add(linearConstraints.get(i).upper().multiply(weight));
            for (var term : linearConstraints.get(i).terms().entrySet()) {
                budget.check();
                terms.merge(term.getKey(), term.getValue().multiply(weight), BigInteger::add);
            }
        }
        terms.values().removeIf(value -> value.signum() == 0);
        if (terms.isEmpty()) return;
        BigInteger common = upper.abs();
        for (BigInteger coefficient : terms.values()) common = common.gcd(coefficient);
        BigInteger divisor = common;
        if (divisor.signum() > 0) {
            terms.replaceAll((key, value) -> value.divide(divisor));
            upper = upper.divide(divisor);
        }
        if (upper.bitLength() > 2048 || terms.values().stream().anyMatch(value -> value.bitLength() > 2048)) return;
        materialConflicts.add(new ExactLinearProgram.Constraint(terms, upper));
    }

    private void enqueue(List<ExactLinearProgram.Constraint> prefix, ExactLinearProgram.Constraint constraint) {
        var next = new ArrayList<>(prefix);
        next.add(constraint);
        pending.add(List.copyOf(next));
    }

    private static ExactLinearProgram.Constraint bound(int variable, BigInteger value, boolean lower) {
        return new ExactLinearProgram.Constraint(Map.of(variable, lower ? BigInteger.ONE.negate() : BigInteger.ONE), lower ? value.negate() : value);
    }

    GraphPlan<K> result() { return result; }
    boolean infeasible() { return infeasible; }

    private boolean finish(boolean proved) {
        complete = true;
        infeasible = proved;
        close();
        return true;
    }

    @Override
    public void close() {
        if (linear != null) linear.close();
        if (propagating != null) propagating.close();
        if (scheduling != null) scheduling.close();
        if (model != null) model.close();
        budget.release(memory);
        memory = 0;
    }
}
