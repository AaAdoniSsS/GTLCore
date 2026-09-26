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
        Map<String, GraphRecipe<K>> compiled = new LinkedHashMap<>();
        model.recipes.forEach(recipe -> compiled.put(recipe.id(), recipe));
        long started = budget.nodes(), allowance = Math.min(32768, budget.remainingWork() / 16);
        int stages = 0;
        // Contract only private seams. Joint outputs remain on the interface;
        // every consumer/exit is retained, including destructive exits.
        for (int pass = 0; pass < 32 && budget.nodes() - started < allowance; pass++) {
            Map<K, List<GraphRecipe<K>>> producers = new HashMap<>(), consumers = new HashMap<>();
            for (var recipe : compiled.values()) {
                budget.check();
                recipe.inputs().keySet().forEach(key -> consumers.computeIfAbsent(key, unused -> new ArrayList<>()).add(recipe));
                recipe.outputs().keySet().forEach(key -> producers.computeIfAbsent(key, unused -> new ArrayList<>()).add(recipe));
            }
            boolean changed = false;
            for (var start : List.copyOf(compiled.values())) {
                budget.check();
                if (budget.nodes() - started >= allowance) break;
                if (!ordinary(start) || !compiled.containsKey(start.id())) continue;
                for (K pending : start.outputs().keySet()) {
                    budget.check();
                    if (model.stock.getOrDefault(pending, 0L) != 0 || model.goal(pending).signum() != 0 || model.external.contains(pending) ||
                            producers.get(pending).size() != 1 || start.inputs().containsKey(pending))
                        continue;
                    var exits = consumers.getOrDefault(pending, List.of());
                    if (exits.isEmpty() || exits.size() > 16 || exits.stream().anyMatch(exit -> !ordinary(exit) || exit == start ||
                            !compiled.containsKey(exit.id()) || exit.outputs().containsKey(pending)))
                        continue;
                    if (!returnsTo(start.inputs().keySet(), pending, consumers, started, allowance)) continue;
                    var macros = new ArrayList<GraphRecipe<K>>();
                    var programs = new ArrayList<PlanStep>();
                    for (var exit : exits) {
                        budget.check();
                        long outputUnits = start.outputs().get(pending), inputUnits = exit.inputs().get(pending);
                        long divisor = BigInteger.valueOf(outputUnits).gcd(BigInteger.valueOf(inputUnits)).longValueExact();
                        long starts = inputUnits / divisor, finishes = outputUnits / divisor;
                        SequenceSummary<K> summary = SequenceSummary.recipe(start).repeat(starts).then(SequenceSummary.recipe(exit).repeat(finishes));
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
                        String id = "@recovery/" + stages + "/" + macros.size();
                        while (compiled.containsKey(id)) id += "/";
                        macros.add(new GraphRecipe<>(id, id, inputs.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), outputs));
                        PlanStep first = bodies.getOrDefault(start.id(), new PlanStep.Batch(start.id(), 1));
                        PlanStep last = bodies.getOrDefault(exit.id(), new PlanStep.Batch(exit.id(), 1));
                        programs.add(new PlanStep.Sequence(List.of(PlanStep.repeat(first, BigInteger.valueOf(starts)), PlanStep.repeat(last, BigInteger.valueOf(finishes)))));
                    }
                    if (macros.size() != exits.size()) continue;
                    compiled.remove(start.id());
                    for (int i = 0; i < exits.size(); i++) {
                        compiled.remove(exits.get(i).id());
                        compiled.put(macros.get(i).id(), macros.get(i));
                        bodies.put(macros.get(i).id(), programs.get(i));
                    }
                    stages++;
                    changed = true;
                    break;
                }
                // Disjoint seams can contract in the same pass. A stale incidence
                // that mentions a removed recipe is skipped until the next pass.
            }
            if (!changed) break;
        }
        addOpenInterfaces(compiled, started, allowance);
        if (bodies.isEmpty()) return;
        budget.note("count_recovery", "recipes=" + model.recipes.size() + "->" + compiled.size() + "; macros=" + bodies.size());
        // Every exit, including destructive ones, stays available. The macro
        // summary retains the real prefix seed requirement. It is a candidate
        // representation only: other interleavings remain in the caller.
        ordinaryView = compiled;
        fuel = CountRecoveryFuel.compile(model, budget);
        begin(fuel == null ? compiled.values() : fuel.recipes());
    }

    private boolean returnsTo(Set<K> inputs, K pending, Map<K, List<GraphRecipe<K>>> consumers, long started, long allowance) {
        Set<K> seen = new HashSet<>();
        Deque<K> queue = new ArrayDeque<>();
        queue.add(pending);
        while (!queue.isEmpty() && budget.nodes() - started < allowance) {
            K key = queue.removeFirst();
            if (!seen.add(key)) continue;
            for (var recipe : consumers.getOrDefault(key, List.of())) {
                budget.check();
                for (K output : recipe.outputs().keySet()) {
                    if (inputs.contains(output)) return true;
                    queue.addLast(output);
                }
            }
        }
        return false;
    }

    /** Public intermediate inventories keep primitive phase entry/exit points as well as complete calls. */
    private void addOpenInterfaces(Map<String, GraphRecipe<K>> compiled, long started, long allowance) {
        Map<K, List<GraphRecipe<K>>> consumers = new HashMap<>();
        List<GraphRecipe<K>> originals = List.copyOf(compiled.values());
        originals.forEach(recipe -> recipe.inputs().keySet().forEach(key -> consumers.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe)));
        Set<String> pairs = new HashSet<>();
        for (var first : originals) {
            if (!ordinary(first)) continue;
            for (K seam : first.outputs().keySet()) {
                if (owner.model.stock.getOrDefault(seam, 0L) == 0 && owner.model.goal(seam).signum() == 0 &&
                        consumers.getOrDefault(seam, List.of()).size() < 2)
                    continue;
                if (first.inputs().containsKey(seam)) continue;
                for (var last : consumers.getOrDefault(seam, List.of())) {
                    budget.check();
                    if (pairs.size() >= 32 || budget.nodes() - started >= allowance) return;
                    if (last == first || !ordinary(last) || last.outputs().containsKey(seam) ||
                            last.outputs().keySet().stream().noneMatch(first.inputs()::containsKey) || !pairs.add(first.id() + "\n" + last.id()))
                        continue;
                    long produced = first.outputs().get(seam), consumed = last.inputs().get(seam);
                    long gcd = BigInteger.valueOf(produced).gcd(BigInteger.valueOf(consumed)).longValueExact();
                    long starts = consumed / gcd, finishes = produced / gcd;
                    SequenceSummary<K> summary = SequenceSummary.recipe(first).repeat(starts).then(SequenceSummary.recipe(last).repeat(finishes));
                    Map<K, Long> inputs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
                    boolean fits = true;
                    for (K key : summary.keys()) {
                        BigInteger input = summary.required(key), output = input.add(summary.delta(key));
                        if (input.compareTo(ExactAmounts.LONG_MAX) > 0 || output.compareTo(ExactAmounts.LONG_MAX) > 0) {
                            fits = false;
                            break;
                        }
                        if (input.signum() > 0) inputs.put(key, input.longValueExact());
                        if (output.signum() > 0) outputs.put(key, output.longValueExact());
                    }
                    if (!fits || outputs.isEmpty()) continue;
                    String id = "@recovery/interface/" + pairs.size();
                    while (compiled.containsKey(id)) id += "/";
                    PlanStep start = bodies.getOrDefault(first.id(), new PlanStep.Batch(first.id(), 1));
                    PlanStep end = bodies.getOrDefault(last.id(), new PlanStep.Batch(last.id(), 1));
                    bodies.put(id, new PlanStep.Sequence(List.of(PlanStep.repeat(start, BigInteger.valueOf(starts)), PlanStep.repeat(end, BigInteger.valueOf(finishes)))));
                    compiled.put(id, new GraphRecipe<>(id, id, inputs.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), outputs));
                }
            }
        }
        if (!pairs.isEmpty()) budget.note("count_recovery_interfaces", "optional_calls=" + pairs.size() + "; primitive_phases_retained");
    }

    private void begin(Collection<GraphRecipe<K>> recipes) {
        search = new IntegerCountSearch<>(new GraphCompiler<>(List.copyOf(recipes)), owner.target, owner.amount,
                owner.stock, owner.seeds, owner.external, Set.of(), owner.preserve, owner.force, budget, owner.started, null, false);
        if (fuel == null) search.importProgramConflicts(owner.model, bodies, owner.knownChoices());
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
