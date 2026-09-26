package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Global source/order search for returned startup loans, with explicit optimality bounds. */
final class SeedOptimization<K> implements AutoCloseable {

    private final GraphCompiler<K> compiler;
    private final GraphPlan<K> original;
    private GraphPlan<K> best, candidate;
    private final Map<K, Long> stock, mandatory;
    private final Set<K> external;
    private final Set<String> excluded;
    private final boolean force;
    private final PlanningBudget budget;
    private final long started, allowance;
    private final Map<String, GraphRecipe<K>> recipes = new LinkedHashMap<>();
    private final Deque<K> pending = new ArrayDeque<>();
    private final Set<K> discovered = new HashSet<>(), produced = new LinkedHashSet<>();
    private final Map<K, BigInteger> raw = new LinkedHashMap<>();
    private final List<K> choices = new ArrayList<>();
    private List<K> quantityKeys;
    private final List<BackwardCoverability.Action<K>> macros = new ArrayList<>();
    private SummaryComputation<K> summarizing;
    private PlanCountComputation counting;
    private Map<String, GraphRecipe<K>> usedRecipes;
    private SequenceSummary<K> summary;
    private BackwardCoverability<K> searching;
    private PlanVerification<K> verifying;
    private PlanStep program;
    private int phase, cardinality, quantityIndex, trials, lowerBound;
    private int unresolvedCardinality = Integer.MAX_VALUE;
    private int[] combination;
    private boolean nextLevel, cardinalityProven, amountsProven = true, complete;
    private BigInteger low, high, middle;
    private Map<K, BigInteger> supplied;
    private long memory;

    SeedOptimization(GraphCompiler<K> compiler, GraphPlan<K> plan, Map<K, Long> stock, Map<K, Long> mandatory,
                     Set<K> external, Set<String> excluded, boolean force, PlanningBudget budget) {
        this.compiler = compiler;
        original = best = plan;
        this.stock = stock;
        this.mandatory = mandatory;
        this.external = external;
        this.excluded = excluded;
        this.force = force;
        this.budget = budget;
        started = budget.nodes();
        allowance = Math.min(262_144, budget.remainingWork() / 8);
        pending.add(plan.target());
        pending.addAll(mandatory.keySet());
        lowerBound = mandatory.size();
    }

    boolean step() {
        if (complete) return true;
        budget.check();
        if (budget.nodes() - started >= allowance) return finish("budget");
        switch (phase) {
            case 0 -> {
                if (!pending.isEmpty()) {
                    K key = pending.removeFirst();
                    if (!discovered.add(key)) return false;
                    for (var recipe : compiler.producers(key)) {
                        budget.check();
                        if (excluded.contains(recipe.id()) || recipes.containsKey(recipe.id())) continue;
                        if (!recipe.configurationInputs().isEmpty() || !recipe.reusableInputs().isEmpty()) return finish("special_input_semantics");
                        long bytes = 192L + 96L * (recipe.inputs().size() + recipe.outputs().size());
                        if (!budget.tryReserve(bytes)) return finish("memory");
                        memory += bytes;
                        recipes.put(recipe.id(), recipe);
                        pending.addAll(recipe.inputs().keySet());
                        produced.addAll(recipe.outputs().keySet());
                    }
                } else {
                    summarizing = new SummaryComputation<>(original.steps(), original.recipes(), budget);
                    phase = 1;
                }
            }
            case 1 -> {
                if (!summarizing.step()) return false;
                summary = summarizing.result();
                macros.add(new BackwardCoverability.Action<>(original.steps(), summary));
                summarizing = null;
                // Net consumables retain their inventory/funded-preview limits.
                // Every other produced startup input is a loan: selected or
                // not, it may not be silently relabelled as a spent raw material.
                for (K key : produced) if (!external.contains(key) && summary.delta(key).signum() >= 0 && !mandatory.containsKey(key))
                    if (!original.feasible() || stock.getOrDefault(key, 0L) > 0) choices.add(key);
                choices.sort(Comparator.comparing(key -> summary.required(key).signum() == 0));
                stock.forEach((key, value) -> raw.put(key, BigInteger.valueOf(value)));
                original.missingExact().forEach((key, value) -> raw.merge(key, value, BigInteger::add));
                for (K key : produced) if (summary.delta(key).signum() >= 0) raw.remove(key);
                mandatory.keySet().forEach(raw::remove);
                combination = new int[0];
                phase = 2;
            }
            case 2 -> {
                int bestFree = (int) best.seeds().keySet().stream().filter(key -> !mandatory.containsKey(key)).count();
                if (cardinality >= bestFree) {
                    cardinalityProven = unresolvedCardinality >= bestFree;
                    if (cardinalityProven) lowerBound = best.seeds().size();
                    quantityKeys = new ArrayList<>(best.seeds().keySet());
                    phase = 5;
                    return false;
                }
                if (nextLevel) {
                    nextLevel = false;
                    cardinality++;
                    if (unresolvedCardinality >= cardinality) lowerBound = mandatory.size() + cardinality;
                    combination = new int[cardinality];
                    for (int i = 0; i < combination.length; i++) combination[i] = i;
                    return false;
                }
                supplied = new LinkedHashMap<>(raw);
                mandatory.forEach((key, value) -> supplied.put(key, BigInteger.valueOf(Math.max(value, stock.getOrDefault(key, 0L)))));
                for (int i : combination) {
                    K key = choices.get(i);
                    supplied.put(key, original.feasible() ? BigInteger.valueOf(stock.getOrDefault(key, 0L)) : ExactAmounts.LONG_MAX);
                }
                advanceCombination();
                beginSearch();
            }
            case 3 -> {
                if (!searching.step()) return false;
                var result = searching.result();
                if (result == BackwardCoverability.Result.WITNESS) {
                    program = searching.witness();
                    counting = new PlanCountComputation(program);
                    summarizing = new SummaryComputation<>(program, recipes, budget);
                    phase = 4;
                } else if (quantityKeys == null) {
                    if (result != BackwardCoverability.Result.CLOSED) unresolvedCardinality = Math.min(unresolvedCardinality, cardinality);
                    phase = 2;
                } else {
                    if (result == BackwardCoverability.Result.CLOSED) low = middle.add(BigInteger.ONE);
                    else {
                        amountsProven = false;
                        quantityIndex++;
                        low = high = null;
                    }
                    phase = 5;
                }
                searching.close();
                searching = null;
            }
            case 4 -> {
                if (verifying != null) {
                    if (!verifying.step()) return false;
                    best = candidate;
                    verifying = null;
                    if (quantityKeys != null) {
                        if (!best.seeds().containsKey(quantityKeys.get(quantityIndex))) {
                            quantityKeys = new ArrayList<>(best.seeds().keySet());
                            quantityIndex = 0;
                            low = high = null;
                            lowerBound = Math.min(lowerBound, best.seeds().size());
                        } else high = BigInteger.valueOf(best.seeds().get(quantityKeys.get(quantityIndex)));
                    }
                    phase = quantityKeys == null ? 2 : 5;
                    return false;
                }
                if (counting != null) {
                    if (!counting.step(budget)) return false;
                    usedRecipes = new LinkedHashMap<>();
                    counting.result().forEach((id, count) -> {
                        if (count.signum() > 0) usedRecipes.put(id, recipes.get(id));
                    });
                    counting = null;
                    return false;
                }
                if (!summarizing.step()) return false;
                candidate = assemble(summarizing.result());
                summarizing = null;
                if (candidate == null) {
                    if (quantityKeys == null) unresolvedCardinality = Math.min(unresolvedCardinality, cardinality);
                    amountsProven = false;
                    if (quantityKeys != null) {
                        quantityIndex++;
                        low = high = null;
                    }
                    phase = quantityKeys == null ? 2 : 5;
                } else verifying = new PlanVerification<>(new GraphPlan<>(candidate.target(), candidate.amount(), true,
                        candidate.steps(), candidate.recipes(), candidate.initialExact(), candidate.seeds(), Map.of(),
                        GraphPlan.Result.FEASIBLE, budget.nodes(), 0), budget);
            }
            case 5 -> {
                if (quantityIndex == quantityKeys.size()) return finish("searched");
                K key = quantityKeys.get(quantityIndex);
                if (low == null) {
                    // Zero is a real alternative for an optional seed. If a
                    // smaller support was previously undecided, skipping zero
                    // would falsely certify a Pareto-minimal quantity vector.
                    low = BigInteger.valueOf(mandatory.getOrDefault(key, 0L));
                    high = BigInteger.valueOf(best.seeds().get(key));
                }
                if (low.compareTo(high) >= 0) {
                    quantityIndex++;
                    low = high = null;
                    return false;
                }
                middle = low.add(high).shiftRight(1);
                supplied = new LinkedHashMap<>(raw);
                best.seeds().forEach((seed, value) -> supplied.put(seed, BigInteger.valueOf(value)));
                supplied.put(key, middle);
                beginSearch();
            }
            default -> throw new IllegalStateException("Invalid seed optimization phase");
        }
        return false;
    }

    private void beginSearch() {
        Map<K, BigInteger> goals = new LinkedHashMap<>();
        supplied.forEach((key, value) -> {
            if (!raw.containsKey(key)) goals.put(key, value);
        });
        BigInteger targetReserve = goals.getOrDefault(original.target(), BigInteger.ZERO);
        if (force) targetReserve = targetReserve.max(supplied.getOrDefault(original.target(), BigInteger.ZERO));
        goals.put(original.target(), targetReserve.add(BigInteger.valueOf(original.amount())));
        searching = new BackwardCoverability<>(List.copyOf(recipes.values()), supplied, goals, external, macros,
                budget, Math.min(32_768, allowance - (budget.nodes() - started)));
        trials++;
        phase = 3;
    }

    private void advanceCombination() {
        int i = combination.length - 1;
        while (i >= 0 && combination[i] == choices.size() - combination.length + i) i--;
        if (i < 0) {
            nextLevel = true;
            return;
        }
        combination[i]++;
        for (int j = i + 1; j < combination.length; j++) combination[j] = combination[j - 1] + 1;
    }

    private GraphPlan<K> assemble(SequenceSummary<K> value) {
        if (force && value.delta(original.target()).compareTo(BigInteger.valueOf(original.amount())) < 0) return null;
        Map<K, Long> seeds = new LinkedHashMap<>(mandatory);
        for (K key : value.keys()) if (!external.contains(key) && value.required(key).signum() > 0) {
            if (!raw.containsKey(key) && value.delta(key).signum() < 0) return null;
            if (produced.contains(key) && value.delta(key).signum() >= 0) {
                if (value.required(key).compareTo(ExactAmounts.LONG_MAX) > 0) return null;
                seeds.merge(key, value.required(key).longValueExact(), Math::max);
                // A different program can turn an old net consumable into a
                // returned catalyst. Count it; never call that a zero-seed plan.
                if (raw.containsKey(key) && !mandatory.containsKey(key)) return null;
            }
        }
        if (quantityKeys != null && !best.seeds().keySet().containsAll(seeds.keySet())) return null;
        Map<K, BigInteger> initial = new LinkedHashMap<>(), missing = new LinkedHashMap<>();
        Set<K> keys = value.keys();
        keys.addAll(seeds.keySet());
        keys.add(original.target());
        for (K key : keys) {
            budget.check();
            BigInteger goal = BigInteger.valueOf(seeds.getOrDefault(key, 0L));
            if (key.equals(original.target())) goal = goal.add(BigInteger.valueOf(original.amount()));
            BigInteger need = value.required(key).max(goal.subtract(value.delta(key)));
            if (!external.contains(key) && need.compareTo(supplied.getOrDefault(key, BigInteger.ZERO)) > 0) return null;
            if (need.signum() > 0) initial.put(key, need);
            BigInteger gap = need.subtract(BigInteger.valueOf(stock.getOrDefault(key, 0L)));
            if (!external.contains(key) && gap.signum() > 0) missing.put(key, gap);
        }
        if (seeds.size() > best.seeds().size() || original.feasible() && !missing.isEmpty()) return null;
        return new GraphPlan<>(original.target(), original.amount(), true, program, usedRecipes, initial, seeds, missing,
                missing.isEmpty() ? GraphPlan.Result.FEASIBLE_NOT_PROVEN_OPTIMAL : GraphPlan.Result.MISSING_INPUT, budget.nodes(), original.planningNanos());
    }

    private boolean finish(String detail) {
        if (!detail.equals("searched")) {
            cardinalityProven = false;
            amountsProven = false;
        }
        complete = true;
        best = best.withSeedOptimality(new GraphPlan.SeedOptimality(Math.min(lowerBound, best.seeds().size()),
                best.seeds().size(), cardinalityProven, amountsProven, !original.feasible()));
        budget.note("global_seeds", detail + "; types=" + original.seeds().size() + "->" + best.seeds().size() +
                "; lower_bound=" + lowerBound + "; cardinality_proven=" + cardinalityProven + "; amounts_proven=" + amountsProven +
                "; all_source_recipes=" + recipes.size() + "; trials=" + trials + "; work=" + (budget.nodes() - started));
        close();
        return true;
    }

    GraphPlan<K> result() {
        return best;
    }

    boolean cardinalityProven() {
        return cardinalityProven;
    }

    boolean amountsProven() {
        return amountsProven;
    }

    int lowerBound() {
        return lowerBound;
    }

    @Override
    public void close() {
        if (searching != null) searching.close();
        searching = null;
        budget.release(memory);
        memory = 0;
    }
}
