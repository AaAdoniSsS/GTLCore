package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Optional private-intermediate macro view. Failure never excludes original interleavings. */
final class CountRecovery<K> implements AutoCloseable {

    private final PlanningBudget budget;
    private final Map<String, PlanStep> bodies = new LinkedHashMap<>();
    private IntegerCountSearch<K> search;
    private final IntegerCountBranch<K> owner;
    private Map<String, GraphRecipe<K>> ordinaryView;
    private CountRecoveryFuel<K> fuel;
    private PlanStep witness;
    private long memory;

    CountRecovery(IntegerCountBranch<K> branch) {
        owner = branch;
        budget = branch.budget;
        try {
            prepare();
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    private void prepare() {
        var branch = owner;
        var model = branch.model;
        long entries = model.recipes.stream().mapToLong(r -> r.inputs().size() + r.outputs().size()).sum();
        long bytes = 2048 + 512L * model.recipes.size() + 384L * entries;
        if (!budget.tryReserve(bytes)) return;
        memory = bytes;
        Map<K, List<GraphRecipe<K>>> producers = new HashMap<>(), consumers = new HashMap<>();
        Map<String, GraphRecipe<K>> compiled = new LinkedHashMap<>();
        for (var recipe : model.recipes) {
            budget.check();
            compiled.put(recipe.id(), recipe);
            recipe.inputs().keySet().forEach(key -> consumers.computeIfAbsent(key, unused -> new ArrayList<>()).add(recipe));
            recipe.outputs().keySet().forEach(key -> producers.computeIfAbsent(key, unused -> new ArrayList<>()).add(recipe));
        }
        Set<String> taken = new HashSet<>();
        for (var start : model.recipes) {
            budget.check();
            if (start.outputs().size() != 1 || !ordinary(start) || taken.contains(start.id())) continue;
            K pending = start.outputs().keySet().iterator().next();
            long units = start.outputs().get(pending);
            if (model.stock.getOrDefault(pending, 0L) != 0 || model.goal(pending).signum() != 0 || model.external.contains(pending) ||
                    producers.get(pending).size() != 1)
                continue;
            var exits = consumers.getOrDefault(pending, List.of());
            if (exits.stream().noneMatch(exit -> exit.outputs().keySet().stream().anyMatch(start.inputs()::containsKey))) continue;
            if (exits.isEmpty() || exits.size() > 16 || exits.stream().anyMatch(exit -> !ordinary(exit) ||
                    taken.contains(exit.id()) || exit == start || exit.inputs().get(pending) != units))
                continue;
            var macros = new ArrayList<GraphRecipe<K>>();
            var programs = new ArrayList<PlanStep>();
            for (var exit : exits) {
                budget.check();
                SequenceSummary<K> summary = SequenceSummary.recipe(start).then(SequenceSummary.recipe(exit));
                Map<K, Long> inputs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
                boolean large = false;
                for (K key : summary.keys()) {
                    BigInteger need = summary.required(key), output = need.add(summary.delta(key));
                    if (need.compareTo(ExactAmounts.LONG_MAX) > 0 || output.compareTo(ExactAmounts.LONG_MAX) > 0) {
                        large = true;
                        break;
                    }
                    if (need.signum() > 0) inputs.put(key, need.longValueExact());
                    if (output.signum() > 0) outputs.put(key, output.longValueExact());
                }
                if (large || outputs.isEmpty()) break;
                String id = "@recovery/" + start.id() + "/" + exit.id();
                while (compiled.containsKey(id)) id += "/";
                macros.add(new GraphRecipe<>(id, id, inputs.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), outputs));
                programs.add(new PlanStep.Sequence(List.of(new PlanStep.Batch(start.id(), 1), new PlanStep.Batch(exit.id(), 1))));
            }
            if (macros.size() != exits.size()) continue;
            taken.add(start.id());
            compiled.remove(start.id());
            for (int i = 0; i < exits.size(); i++) {
                taken.add(exits.get(i).id());
                compiled.remove(exits.get(i).id());
                compiled.put(macros.get(i).id(), macros.get(i));
                bodies.put(macros.get(i).id(), programs.get(i));
            }
        }
        if (bodies.isEmpty()) return;
        budget.note("count_recovery", "recipes=" + model.recipes.size() + "->" + compiled.size() + "; macros=" + bodies.size());
        // Every exit, including destructive ones, stays available. The macro
        // summary retains the real prefix seed requirement. It is a candidate
        // representation only: other interleavings remain in the caller.
        ordinaryView = compiled;
        fuel = CountRecoveryFuel.compile(model, budget);
        begin(fuel == null ? compiled.values() : fuel.recipes());
    }

    private void begin(Collection<GraphRecipe<K>> recipes) {
        search = new IntegerCountSearch<>(new GraphCompiler<>(List.copyOf(recipes)), owner.target, owner.amount,
                owner.stock, owner.seeds, owner.external, Set.of(), owner.preserve, owner.force, budget, owner.started, null, false);
    }

    private static boolean ordinary(GraphRecipe<?> recipe) {
        return recipe.configurationInputs().isEmpty() && recipe.reusableInputs().isEmpty();
    }

    boolean step() {
        if (search == null) return true;
        if (!search.step()) return false;
        var plan = search.result();
        if (plan != null && plan.feasible()) {
            witness = fuel == null ? expand(plan.steps(), new IdentityHashMap<>()) : fuel.lift(plan);
            budget.note("count_recovery", "lifted_witness; macros=" + bodies.size());
        }
        search.close();
        search = null;
        if (fuel != null && witness == null) {
            fuel = null;
            begin(ordinaryView.values());
            return false;
        }
        return true;
    }

    private PlanStep expand(PlanStep step, Map<PlanStep, PlanStep> memo) {
        PlanStep cached = memo.get(step);
        if (cached != null) return cached;
        budget.check();
        PlanStep result = step;
        if (step instanceof PlanStep.Batch batch && bodies.containsKey(batch.recipe()))
            result = PlanStep.repeat(bodies.get(batch.recipe()), BigInteger.valueOf(batch.runs()));
        else if (step instanceof PlanStep.Repeat repeat) result = new PlanStep.Repeat(expand(repeat.body(), memo), repeat.times());
        else if (step instanceof PlanStep.Sequence sequence) {
            var children = new ArrayList<PlanStep>();
            for (var child : sequence.children()) children.add(expand(child, memo));
            result = new PlanStep.Sequence(children);
        }
        memo.put(step, result);
        return result;
    }

    PlanStep witness() {
        return witness;
    }

    @Override
    public void close() {
        if (search != null) search.close();
        search = null;
        budget.release(memory);
        memory = 0;
    }
}
