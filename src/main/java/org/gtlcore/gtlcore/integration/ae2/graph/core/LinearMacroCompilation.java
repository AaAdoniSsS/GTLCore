package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Optional exact contraction of unit-rate chains; every witness expands to real recipes. */
final class LinearMacroCompilation<K> {

    private final Map<String, GraphRecipe<K>> original;
    private final PlanningBudget budget;
    private final Set<K> goals;
    private final Map<K, List<GraphRecipe<K>>> producers = new HashMap<>(), consumers = new HashMap<>();
    private final Map<String, List<GraphRecipe<K>>> bodies = new LinkedHashMap<>();
    private final Map<String, GraphRecipe<K>> combined = new LinkedHashMap<>();
    private Iterator<GraphRecipe<K>> iterator;
    private final List<GraphRecipe<K>> chain = new ArrayList<>();
    private final Set<K> visited = new HashSet<>();
    private K input, output;
    private int phase, visits;

    LinearMacroCompilation(Map<String, GraphRecipe<K>> recipes, K target, Set<K> seeds, PlanningBudget budget) {
        original = new LinkedHashMap<>(recipes);
        this.budget = budget;
        goals = new HashSet<>(seeds);
        goals.add(target);
        iterator = original.values().iterator();
        budget.reserve(128L + 64L * recipes.size());
    }

    boolean step() {
        budget.check();
        if (phase == 0) {
            if (iterator.hasNext()) {
                GraphRecipe<K> recipe = iterator.next();
                for (K key : recipe.inputs().keySet()) consumers.computeIfAbsent(key, k -> new ArrayList<>()).add(recipe);
                for (K key : recipe.outputs().keySet()) producers.computeIfAbsent(key, k -> new ArrayList<>()).add(recipe);
                budget.reserve(80L * (recipe.inputs().size() + recipe.outputs().size()));
                return false;
            }
            iterator = original.values().iterator();
            phase = 1;
        }
        if (phase == 1) {
            if (++visits >= 32_768 || !iterator.hasNext() && chain.isEmpty()) {
                combined.putAll(original);
                phase = 2;
                return true;
            }
            if (chain.isEmpty()) {
                GraphRecipe<K> end = iterator.next();
                if (!unit(end)) return false;
                output = end.outputs().keySet().iterator().next();
                var next = consumers.getOrDefault(output, List.of());
                if (!goals.contains(output) && producers.get(output).size() == 1 && next.size() == 1 && unit(next.get(0))) return false;
                input = end.inputs().keySet().iterator().next();
                visited.clear();
                visited.add(output);
                chain.add(end);
            }
            if (!visited.add(input)) {
                chain.clear();
                return false;
            }
            var prior = producers.getOrDefault(input, List.of());
            if (chain.size() < 2048 && prior.size() == 1 && unit(prior.get(0))) {
                GraphRecipe<K> recipe = prior.get(0);
                chain.add(recipe);
                input = recipe.inputs().keySet().iterator().next();
                return false;
            }
            if (chain.size() > 1) {
                Collections.reverse(chain);
                String id = "@linear/" + bodies.size();
                while (original.containsKey(id)) id += "/";
                bodies.put(id, List.copyOf(chain));
                combined.put(id, new GraphRecipe<>(id, id, List.of(new GraphRecipe.Slot<>(input, 1L)), Map.of(output, 1L)));
                budget.reserve(192L + 16L * chain.size());
            }
            chain.clear();
            return false;
        }
        return true;
    }

    private static boolean unit(GraphRecipe<?> recipe) {
        return recipe.configurationInputs().isEmpty() && recipe.inputs().size() == 1 && recipe.outputs().size() == 1 &&
                recipe.inputs().values().iterator().next() == 1L && recipe.outputs().values().iterator().next() == 1L;
    }

    Map<String, GraphRecipe<K>> recipes() {
        return combined;
    }

    Map<String, BigInteger> counts(String id) {
        var body = bodies.get(id);
        if (body == null) return Map.of(id, BigInteger.ONE);
        Map<String, BigInteger> result = new LinkedHashMap<>();
        for (var recipe : body) {
            budget.check();
            result.merge(recipe.id(), BigInteger.ONE, BigInteger::add);
        }
        return Map.copyOf(result);
    }

    PlanStep expand(PlanStep step) {
        long[] retained = { 0 };
        boolean complete = false;
        try {
            PlanStep result = PlanRewrite.batches(step, batch -> {
                if (!bodies.containsKey(batch.recipe())) return batch;
                var children = new ArrayList<PlanStep>();
                for (GraphRecipe<K> recipe : bodies.get(batch.recipe())) {
                    budget.check();
                    children.add(PlanStep.batch(recipe.id(), BigInteger.valueOf(batch.runs())));
                }
                long bytes = 64L + 64L * children.size();
                budget.reserve(bytes);
                retained[0] += bytes;
                return new PlanStep.Sequence(children);
            }, budget);
            complete = true;
            return result;
        } finally {
            if (!complete) budget.release(retained[0]);
        }
    }
}
