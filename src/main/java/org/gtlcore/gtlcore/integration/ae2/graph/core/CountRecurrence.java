package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Construct repeated subprograms before expanding their trace. Failure is only a heuristic miss. */
final class CountRecurrence<K> implements AutoCloseable {

    private final RecipeCountModel<K> model;
    private final BigInteger[] counts;
    private final List<SequenceSummary<K>> recipes;
    private final PlanningBudget budget;
    private final BitSet included = new BitSet();
    private final IdentityHashMap<PlanStep, Integer> depths = new IdentityHashMap<>();
    private SequenceSummary<K> summary = SequenceSummary.empty();
    private PlanStep body, witness;
    private BigInteger period;
    private long memory;
    private boolean complete;

    CountRecurrence(RecipeCountModel<K> model, BigInteger[] counts, List<SequenceSummary<K>> recipes, PlanningBudget budget) {
        this.model = model;
        this.counts = counts;
        this.recipes = recipes;
        this.budget = budget;
        long bytes = 1024L + 512L * counts.length * model.keys.size();
        if (counts.length > 120 || Arrays.stream(counts).filter(v -> v.signum() > 0).distinct().count() < 3 ||
                model.recipes.stream().anyMatch(GraphRecipe::batchSensitiveInputs) || !budget.tryReserve(bytes))
            complete = true;
        else memory = bytes;
    }

    boolean step() {
        if (complete) return true;
        BigInteger next = BigInteger.ZERO;
        for (int i = 0; i < counts.length; i++) if (!included.get(i)) next = next.max(counts[i]);
        if (next.signum() == 0) {
            if (body != null && funded(scale(summary, period))) {
                PlanStep candidate = repeat(body, period);
                if (depth(candidate) <= 120) witness = candidate;
            }
            complete = true;
            if (witness != null) budget.note("count_recurrence", "verified_shared_program; recipes=" + included.cardinality());
            return true;
        }
        if (period != null && period.remainder(next).signum() != 0) return stop();
        BigInteger ratio = period == null ? BigInteger.ZERO : period.divide(next);
        for (int i = 0; i < counts.length; i++) if (!included.get(i) && counts[i].equals(next)) {
            Set<BigInteger> cuts = new LinkedHashSet<>(List.of(ratio.divide(BigInteger.TWO), ratio, BigInteger.ZERO));
            for (BigInteger before : cuts) {
                budget.check();
                BigInteger after = ratio.subtract(before);
                SequenceSummary<K> candidate = scale(summary, before).then(recipes.get(i)).then(scale(summary, after));
                if (!funded(candidate)) continue;
                List<PlanStep> children = new ArrayList<>();
                if (before.signum() > 0) children.add(repeat(body, before));
                children.add(new PlanStep.Batch(model.recipes.get(i).id(), 1));
                if (after.signum() > 0) children.add(repeat(body, after));
                body = children.size() == 1 ? children.get(0) : new PlanStep.Sequence(children);
                // Leave headroom for assembly and the persisted program envelope.
                if (depth(body) > 120) return stop();
                summary = candidate;
                included.set(i);
                period = next;
                return false;
            }
        }
        return stop();
    }

    private boolean funded(SequenceSummary<K> candidate) {
        for (var entry : candidate.required().entrySet()) {
            budget.check();
            if (!model.external.contains(entry.getKey()) && entry.getValue().compareTo(BigInteger.valueOf(model.stock.getOrDefault(entry.getKey(), 0L))) > 0) return false;
        }
        return true;
    }

    private SequenceSummary<K> scale(SequenceSummary<K> source, BigInteger times) {
        if (times.signum() == 0) return SequenceSummary.empty();
        if (times.equals(BigInteger.ONE)) return source;
        Map<K, BigInteger> required = new LinkedHashMap<>(), delta = new LinkedHashMap<>(), peak = new LinkedHashMap<>();
        for (K key : source.keys()) {
            budget.check();
            BigInteger change = source.delta(key);
            required.put(key, source.required(key).add(change.negate().max(BigInteger.ZERO).multiply(times.subtract(BigInteger.ONE))));
            delta.put(key, change.multiply(times));
            peak.put(key, source.peak(key).add(change.max(BigInteger.ZERO).multiply(times.subtract(BigInteger.ONE))));
        }
        return new SequenceSummary<>(required, delta, peak);
    }

    private int depth(PlanStep step) {
        Integer known = depths.get(step);
        if (known != null) return known;
        budget.check();
        int result = 0;
        if (step instanceof PlanStep.Repeat repeat) result = 1 + depth(repeat.body());
        else if (step instanceof PlanStep.Sequence sequence)
            for (PlanStep child : sequence.children()) result = Math.max(result, 1 + depth(child));
        depths.put(step, result);
        return result;
    }

    private static PlanStep repeat(PlanStep body, BigInteger times) {
        return times.equals(BigInteger.ONE) ? body : PlanStep.repeat(body, times);
    }

    private boolean stop() {
        complete = true;
        return true;
    }

    PlanStep witness() {
        return witness;
    }

    @Override
    public void close() {
        budget.release(memory);
        memory = 0;
    }
}
