package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Compressed scheduling of fixed integer counts, with exact small-multiset fallback. */
final class CountSchedule<K> implements AutoCloseable {

    enum Result {
        WITNESS,
        DEAD,
        UNKNOWN
    }

    private final RecipeCountModel<K> model;
    private final PlanningBudget budget;
    private final BigInteger[] original;
    private final boolean balancedFirst;
    private final Map<String, GraphRecipe<K>> recipes = new LinkedHashMap<>();
    private final List<SequenceSummary<K>> summaries = new ArrayList<>();
    private final Map<K, List<Integer>> consumers = new HashMap<>();
    private final Map<K, BigInteger> held = new LinkedHashMap<>();
    private final List<PlanStep> program = new ArrayList<>(), pass = new ArrayList<>();
    private final Deque<State> pending = new ArrayDeque<>();
    private final Map<List<BigInteger>, List<BitSet>> seen = new HashMap<>();
    private PartialOrder<K> partialOrder;
    private int labels;
    private BigInteger[] remaining, used;
    private SummaryComputation<K> summarizing;
    private PlanStep passBody, witness;
    private int attempt, cursor, passes;
    private boolean exact, startupChecked;
    private int[] components;
    private int independentComponents;
    private long orderedAway;
    private Result result;
    private long memory;

    CountSchedule(RecipeCountModel<K> model, BigInteger[] counts, PlanningBudget budget) {
        this(model, counts, budget, false);
    }

    CountSchedule(RecipeCountModel<K> model, BigInteger[] counts, PlanningBudget budget, boolean balancedFirst) {
        this.model = model;
        this.budget = budget;
        this.balancedFirst = balancedFirst;
        original = counts.clone();
        if (!budget.tryReserve(4L << 20)) {
            result = Result.UNKNOWN;
            return;
        }
        memory = 4L << 20;
        for (GraphRecipe<K> recipe : model.recipes) {
            recipes.put(recipe.id(), recipe);
            SequenceSummary<K> summary = SequenceSummary.recipe(recipe);
            int index = summaries.size();
            summary.delta().forEach((key, amount) -> {
                if (amount.signum() < 0 && !model.external.contains(key))
                    consumers.computeIfAbsent(key, unused -> new ArrayList<>()).add(index);
            });
            summaries.add(summary);
        }
        reset();
    }

    boolean step() {
        budget.check();
        if (result != null) return true;
        if (!startupChecked) {
            startupChecked = true;
            if (blockedStartup()) return finish(Result.DEAD);
        }
        if (exact) return exactStep();
        if (summarizing != null) {
            if (!summarizing.step()) return false;
            SequenceSummary<K> summary = summarizing.result();
            summarizing = null;
            BigInteger extra = null;
            for (int i = 0; i < used.length; i++) if (used[i].signum() > 0) {
                BigInteger bound = remaining[i].divide(used[i]);
                extra = extra == null ? bound : extra.min(bound);
            }
            extra = limit(summary, extra == null ? BigInteger.ZERO : extra);
            if (extra.signum() > 0) {
                apply(summary, extra);
                for (int i = 0; i < used.length; i++) remaining[i] = remaining[i].subtract(used[i].multiply(extra));
            }
            program.add(PlanStep.repeat(passBody, extra.add(BigInteger.ONE)));
            pass.clear();
            Arrays.fill(used, BigInteger.ZERO);
            cursor = 0;
            if (done()) {
                witness = new PlanStep.Sequence(program);
                return finish(Result.WITNESS);
            }
            if (++passes >= 512) return nextAttempt();
            return false;
        }
        if (cursor < model.recipes.size()) {
            int count = model.recipes.size(), orders = Math.min(4, count);
            int variant = attempt % (2 * orders), mode = attempt / (2 * orders);
            if (!balancedFirst && mode < 3) mode = (mode + 2) % 3;
            int rotation = variant % orders;
            int recipe = (cursor++ + rotation) % count;
            if (variant >= orders) recipe = count - 1 - recipe;
            BigInteger runs = limit(summaries.get(recipe), remaining[recipe]);
            // Maximal batches can drain a shared cycle resource into one branch
            // before its competing producer runs. Try leaving some funded work
            // for that branch; only the resulting exact witness is accepted.
            if (mode == 0 && runs.signum() > 0) runs = share(recipe, runs);
            else if (mode == 1 && runs.signum() > 0) runs = runs.divide(BigInteger.TWO).max(BigInteger.ONE);
            else if (mode == 3) runs = runs.min(BigInteger.ONE);
            if (runs.signum() > 0) {
                apply(summaries.get(recipe), runs);
                remaining[recipe] = remaining[recipe].subtract(runs);
                used[recipe] = used[recipe].add(runs);
                pass.add(PlanStep.batch(model.recipes.get(recipe).id(), runs));
            }
            return false;
        }
        if (pass.isEmpty()) {
            if (done()) {
                witness = new PlanStep.Sequence(program);
                return finish(Result.WITNESS);
            }
            return nextAttempt();
        }
        passBody = new PlanStep.Sequence(pass);
        summarizing = new SummaryComputation<>(passBody, recipes, budget);
        return false;
    }

    private BigInteger limit(SequenceSummary<K> summary, BigInteger maximum) {
        if (maximum.signum() == 0) return maximum;
        for (K key : summary.keys()) if (!model.external.contains(key)) {
            budget.check();
            BigInteger available = held.getOrDefault(key, BigInteger.ZERO);
            if (available.compareTo(summary.required(key)) < 0) return BigInteger.ZERO;
            if (summary.delta(key).signum() < 0)
                maximum = maximum.min(available.subtract(summary.required(key)).divide(summary.delta(key).negate()).add(BigInteger.ONE));
        }
        return maximum;
    }

    private BigInteger share(int recipe, BigInteger maximum) {
        for (var delta : summaries.get(recipe).delta().entrySet()) {
            if (delta.getValue().signum() >= 0) continue;
            List<Integer> peers = consumers.getOrDefault(delta.getKey(), List.of());
            if (peers.size() < 2) continue;
            BigInteger consumption = BigInteger.ZERO;
            for (int peer : peers) {
                budget.check();
                consumption = consumption.subtract(summaries.get(peer).delta(delta.getKey()).multiply(remaining[peer]));
            }
            if (consumption.signum() > 0) {
                BigInteger share = held.getOrDefault(delta.getKey(), BigInteger.ZERO).multiply(remaining[recipe]).divide(consumption);
                maximum = maximum.min(share.max(BigInteger.ONE));
            }
        }
        return maximum;
    }

    private void apply(SequenceSummary<K> summary, BigInteger copies) {
        for (var delta : summary.delta().entrySet()) if (!model.external.contains(delta.getKey())) {
            budget.check();
            held.merge(delta.getKey(), delta.getValue().multiply(copies), BigInteger::add);
        }
    }

    private void reset() {
        remaining = original.clone();
        used = new BigInteger[original.length];
        Arrays.fill(used, BigInteger.ZERO);
        held.clear();
        model.stock.forEach((key, amount) -> held.put(key, BigInteger.valueOf(amount)));
        program.clear();
        pass.clear();
        passes = cursor = 0;
    }

    private boolean done() {
        return Arrays.stream(remaining).allMatch(value -> value.signum() == 0);
    }

    private boolean blockedStartup() {
        if (model.recipes.stream().anyMatch(recipe -> !recipe.configurationInputs().isEmpty() || !recipe.reusableInputs().isEmpty())) return false;
        Map<K, BigInteger> upper = new HashMap<>();
        model.stock.forEach((key, value) -> upper.put(key, BigInteger.valueOf(value)));
        BitSet available = new BitSet();
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < original.length; i++) {
                budget.check();
                if (original[i].signum() == 0 || available.get(i)) continue;
                GraphRecipe<K> recipe = model.recipes.get(i);
                if (recipe.inputs().entrySet().stream().anyMatch(e -> !model.external.contains(e.getKey()) &&
                        upper.getOrDefault(e.getKey(), BigInteger.ZERO).compareTo(BigInteger.valueOf(e.getValue())) < 0))
                    continue;
                available.set(i);
                BigInteger count = original[i];
                // Optimistically grant ALL outputs without consuming any input.
                // A transition still unreachable here cannot occur in any
                // ordering of this fixed multiset, even with enormous counts.
                recipe.outputs().forEach((key, value) -> upper.merge(key, BigInteger.valueOf(value).multiply(count), BigInteger::add));
                changed = true;
            }
        } while (changed);
        for (int i = 0; i < original.length; i++) if (original[i].signum() > 0 && !available.get(i)) return true;
        return false;
    }

    private boolean nextAttempt() {
        boolean early = ++attempt < 8 * Math.min(4, model.recipes.size());
        if (early && (attempt != 2 || !smallMultiset())) {
            reset();
            return false;
        }
        // Only exhaustive exploration may turn a scheduling failure into a
        // counterexample. A greedy failure, depth cap, or memory cap never does.
        if (model.recipes.stream().anyMatch(recipe -> !recipe.configurationInputs().isEmpty() || !recipe.reusableInputs().isEmpty())) return finish(Result.UNKNOWN);
        components = independentComponents();
        BitSet activeComponents = new BitSet();
        for (int i = 0; i < original.length; i++) if (original[i].signum() > 0) {
            activeComponents.set(components[i]);
        }
        independentComponents = activeComponents.cardinality();
        long bytes = 4096 + 64L * original.length + (long) original.length * original.length / 4;
        if (!budget.tryReserve(bytes)) {
            if (!early) return finish(Result.UNKNOWN);
            independentComponents = 0;
            reset();
            return false;
        }
        memory += bytes;
        partialOrder = new PartialOrder<>(summaries, budget);
        List<BigInteger> counts = List.copyOf(Arrays.asList(original));
        if (!remember(counts, new BitSet())) return finish(Result.UNKNOWN);
        pending.add(new State(counts, new BitSet(), null, -1));
        exact = true;
        return false;
    }

    private boolean smallMultiset() {
        if (model.recipes.stream().anyMatch(recipe -> !recipe.configurationInputs().isEmpty())) return false;
        BigInteger states = BigInteger.ONE, total = BigInteger.ZERO;
        int active = 0;
        for (BigInteger count : original) if (count.signum() > 0) {
            budget.check();
            states = states.multiply(count.add(BigInteger.ONE));
            total = total.add(count);
            if (++active > 12 || total.compareTo(BigInteger.valueOf(24)) > 0 || states.compareTo(BigInteger.valueOf(2048)) > 0) return false;
        }
        return true;
    }

    private boolean exactStep() {
        if (pending.isEmpty()) return finish(Result.DEAD);
        State state = pending.removeLast();
        if (state.counts.stream().allMatch(value -> value.signum() == 0)) {
            var path = new ArrayList<PlanStep>();
            for (State current = state; current.parent != null; current = current.parent)
                path.add(new PlanStep.Batch(model.recipes.get(current.recipe).id(), 1));
            Collections.reverse(path);
            witness = new PlanStep.Sequence(path);
            return finish(Result.WITNESS);
        }
        held.clear();
        model.stock.forEach((key, value) -> held.put(key, BigInteger.valueOf(value)));
        for (int i = 0; i < original.length; i++) apply(summaries.get(i), original[i].subtract(state.counts.get(i)));
        int component = -1;
        for (int i = 0; i < original.length; i++) if (state.counts.get(i).signum() > 0) {
            component = components[i];
            break;
        }
        BitSet sleeping = (BitSet) state.sleeping.clone();
        BitSet available = new BitSet(), active = new BitSet();
        for (int i = 0; i < original.length; i++) if (state.counts.get(i).signum() > 0 && components[i] == component) {
            active.set(i);
            if (limit(summaries.get(i), BigInteger.ONE).signum() > 0) available.set(i);
        }
        BitSet persistent = partialOrder.persistent(active, available, held, model.external);
        var children = new ArrayList<State>();
        for (int i = 0; i < original.length; i++) if (state.counts.get(i).signum() > 0) {
            if (components[i] != component) {
                orderedAway++;
                continue;
            }
            if (!available.get(i)) continue;
            if (sleeping.get(i) || !persistent.get(i)) {
                orderedAway++;
                continue;
            }
            var next = new ArrayList<>(state.counts);
            next.set(i, next.get(i).subtract(BigInteger.ONE));
            var frozen = List.copyOf(next);
            BitSet childSleep = partialOrder.after(sleeping, i);
            sleeping.set(i);
            if (remember(frozen, childSleep)) children.add(new State(frozen, childSleep, state, i));
            if (labels >= 8192 || result != null) return finish(Result.UNKNOWN);
        }
        // Explore the earlier alternative before its sleeping equivalents.
        for (int i = children.size() - 1; i >= 0; i--) pending.addLast(children.get(i));
        return false;
    }

    private boolean remember(List<BigInteger> counts, BitSet sleeping) {
        List<BitSet> previous = seen.get(counts);
        if (previous != null && previous.stream().anyMatch(old -> PartialOrder.subset(old, sleeping))) return false;
        long bytes = 160 + 48L * original.length;
        if (!budget.tryReserve(bytes)) {
            result = Result.UNKNOWN;
            return false;
        }
        memory += bytes;
        labels++;
        // A marking reached with MORE sleeping actions must not suppress a
        // later arrival that can explore additional orders.
        if (previous == null) seen.put(counts, previous = new ArrayList<>());
        previous.removeIf(old -> PartialOrder.subset(sleeping, old));
        previous.add((BitSet) sleeping.clone());
        return true;
    }

    private record State(List<BigInteger> counts, BitSet sleeping, State parent, int recipe) {}

    private int[] independentComponents() {
        int[] root = new int[original.length];
        for (int i = 0; i < root.length; i++) root[i] = i;
        Map<K, Integer> touched = new HashMap<>();
        for (int i = 0; i < original.length; i++) if (original[i].signum() > 0) {
            Set<K> keys = new HashSet<>(model.recipes.get(i).inputs().keySet());
            keys.addAll(model.recipes.get(i).outputs().keySet());
            for (K key : keys) {
                budget.check();
                Integer other = touched.putIfAbsent(key, i);
                if (other != null) {
                    int a = component(root, i), b = component(root, other);
                    root[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        for (int i = 0; i < root.length; i++) root[i] = component(root, i);
        return root;
    }

    private static int component(int[] root, int id) {
        while (root[id] != id) {
            root[id] = root[root[id]];
            id = root[id];
        }
        return id;
    }

    PlanStep witness() {
        return witness;
    }

    Result result() {
        return result;
    }

    private boolean finish(Result value) {
        if (exact) budget.note("count_schedule_por", "components=" + independentComponents +
                "; states=" + seen.size() + "; sleep_labels=" + labels + "; independent_interleavings_skipped=" + orderedAway + "; result=" + value);
        result = value;
        close();
        return true;
    }

    @Override
    public void close() {
        budget.release(memory);
        memory = 0;
    }
}
