package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Factor interchangeable recovery/burn choices from independent raw-fed routes. Witness only. */
final class CountRecoveryFuel<K> {

    private record Route<K>(String id, GraphRecipe<K> start, GraphRecipe<K> returned, GraphRecipe<K> burned) {}

    private final RecipeCountModel<K> original;
    private final PlanningBudget budget;
    private final List<Route<K>> routes = new ArrayList<>();
    private final Map<String, GraphRecipe<K>> recipes = new LinkedHashMap<>();
    private K catalyst, fuel;
    private long catalystUnits, fuelUnits;

    private CountRecoveryFuel(RecipeCountModel<K> original, PlanningBudget budget) {
        this.original = original;
        this.budget = budget;
    }

    static <K> CountRecoveryFuel<K> compile(RecipeCountModel<K> model, PlanningBudget budget) {
        var view = new CountRecoveryFuel<>(model, budget);
        return view.prepare() ? view : null;
    }

    private boolean prepare() {
        Map<K, List<GraphRecipe<K>>> producers = new HashMap<>(), consumers = new HashMap<>();
        for (var recipe : original.recipes) {
            budget.check();
            recipes.put(recipe.id(), recipe);
            recipe.inputs().keySet().forEach(key -> consumers.computeIfAbsent(key, unused -> new ArrayList<>()).add(recipe));
            recipe.outputs().keySet().forEach(key -> producers.computeIfAbsent(key, unused -> new ArrayList<>()).add(recipe));
        }
        Set<String> taken = new HashSet<>();
        for (var start : original.recipes) {
            budget.check();
            if (!ordinary(start) || start.outputs().size() != 1 || taken.contains(start.id())) continue;
            K pending = start.outputs().keySet().iterator().next();
            var exits = consumers.getOrDefault(pending, List.of());
            if (exits.size() != 2 || producers.get(pending).size() != 1 || original.stock.getOrDefault(pending, 0L) != 0 ||
                    original.goal(pending).signum() != 0 || original.external.contains(pending))
                continue;
            var burned = exits.get(0).inputs().size() == 1 ? exits.get(0) : exits.get(1);
            var returned = burned == exits.get(0) ? exits.get(1) : exits.get(0);
            if (!ordinary(burned) || !ordinary(returned) || taken.contains(burned.id()) || taken.contains(returned.id()) ||
                    burned == start || returned == start || burned.inputs().size() != 1 || returned.inputs().size() != 2 ||
                    !burned.inputs().get(pending).equals(start.outputs().get(pending)) || !returned.inputs().get(pending).equals(start.outputs().get(pending)))
                continue;
            Map<K, Long> difference = new LinkedHashMap<>(returned.outputs());
            if (burned.outputs().entrySet().stream().anyMatch(e -> !e.getValue().equals(difference.remove(e.getKey()))) || difference.size() != 1) continue;
            K cat = difference.keySet().iterator().next();
            long catUnits = difference.get(cat);
            K cost = returned.inputs().keySet().stream().filter(key -> !key.equals(pending)).findFirst().orElseThrow();
            long costUnits = returned.inputs().get(cost);
            if (cat.equals(cost) || start.inputs().getOrDefault(cat, 0L) != catUnits || start.inputs().containsKey(cost) ||
                    original.external.contains(cat) || original.external.contains(cost))
                continue;
            if (start.inputs().keySet().stream().anyMatch(key -> !key.equals(cat) && producers.containsKey(key))) continue;
            if (catalyst != null && (!catalyst.equals(cat) || !fuel.equals(cost) || catalystUnits != catUnits || fuelUnits != costUnits)) continue;
            catalyst = cat;
            fuel = cost;
            catalystUnits = catUnits;
            fuelUnits = costUnits;
            String id = "@recovery_fuel/" + routes.size();
            while (recipes.containsKey(id)) id += "/";
            Map<K, Long> outputs = new LinkedHashMap<>(burned.outputs());
            outputs.put(cat, catUnits);
            recipes.put(id, new GraphRecipe<>(id, id, start.slots(), outputs));
            for (var used : List.of(start, returned, burned)) {
                taken.add(used.id());
                recipes.remove(used.id());
            }
            routes.add(new Route<>(id, start, returned, burned));
        }
        if (routes.size() < 2) return false;
        // Only these routes may alter or reserve the common recovery account.
        // In particular, no future fuel/catalyst production is anticipated.
        for (var recipe : original.recipes) if (!taken.contains(recipe.id())) {
            budget.check();
            if (recipe.inputs().containsKey(catalyst) || recipe.outputs().containsKey(catalyst) ||
                    recipe.inputs().containsKey(fuel) || recipe.outputs().containsKey(fuel))
                return false;
        }
        budget.note("count_recovery_fuel", "routes=" + routes.size() + "; recipes=" + original.recipes.size() + "->" + recipes.size());
        return true;
    }

    private static boolean ordinary(GraphRecipe<?> recipe) {
        return recipe.configurationInputs().isEmpty() && recipe.reusableInputs().isEmpty();
    }

    Collection<GraphRecipe<K>> recipes() {
        return recipes.values();
    }

    PlanStep lift(GraphPlan<K> plan) {
        Map<String, BigInteger> counts = plan.patternTimesExact();
        BigInteger total = BigInteger.ZERO;
        for (var route : routes) total = total.add(counts.getOrDefault(route.id, BigInteger.ZERO));
        BigInteger fuelAvailable = BigInteger.valueOf(original.stock.getOrDefault(fuel, 0L)).subtract(original.goal(fuel));
        BigInteger catAvailable = BigInteger.valueOf(original.stock.getOrDefault(catalyst, 0L)).subtract(original.goal(catalyst));
        if (fuelAvailable.signum() < 0 || catAvailable.signum() < 0) return null;
        BigInteger returned = total.min(fuelAvailable.divide(BigInteger.valueOf(fuelUnits)));
        BigInteger burned = total.subtract(returned);
        if (burned.compareTo(catAvailable.divide(BigInteger.valueOf(catalystUnits))) > 0) return null;
        List<PlanStep> first = new ArrayList<>(), last = new ArrayList<>();
        Set<String> removed = new HashSet<>();
        for (var route : routes) {
            budget.check();
            removed.add(route.id);
            BigInteger n = counts.getOrDefault(route.id, BigInteger.ZERO), recover = n.min(returned);
            returned = returned.subtract(recover);
            if (recover.signum() > 0) first.add(pair(route.start, route.returned, recover));
            if (n.compareTo(recover) > 0) last.add(pair(route.start, route.burned, n.subtract(recover)));
        }
        first.addAll(last);
        first.add(remove(plan.steps(), removed, new IdentityHashMap<>()));
        budget.note("count_recovery_fuel", "lifted; runs=" + total + "; burns=" + burned);
        return new PlanStep.Sequence(first);
    }

    private PlanStep pair(GraphRecipe<K> start, GraphRecipe<K> end, BigInteger count) {
        return PlanStep.repeat(new PlanStep.Sequence(List.of(new PlanStep.Batch(start.id(), 1), new PlanStep.Batch(end.id(), 1))), count);
    }

    private PlanStep remove(PlanStep step, Set<String> removed, Map<PlanStep, PlanStep> memo) {
        PlanStep cached = memo.get(step);
        if (cached != null) return cached;
        budget.check();
        PlanStep result = step;
        if (step instanceof PlanStep.Batch batch && removed.contains(batch.recipe())) result = new PlanStep.Sequence(List.of());
        else if (step instanceof PlanStep.Repeat repeat) result = new PlanStep.Repeat(remove(repeat.body(), removed, memo), repeat.times());
        else if (step instanceof PlanStep.Sequence sequence) {
            var children = new ArrayList<PlanStep>();
            for (var child : sequence.children()) children.add(remove(child, removed, memo));
            result = new PlanStep.Sequence(children);
        }
        memo.put(step, result);
        return result;
    }
}
