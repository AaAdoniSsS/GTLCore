package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.*;

import java.util.*;

/** Delayed physical machines with an independent material ledger, including randomized return order. */
public final class GraphPipelineTest {

    private static int checks;

    public static void run() {
        partialStages();
        reservePrefix();
        reserveOnlyRequiredAmount();
        prefixPeak();
        delayedSideOutput();
        independentBlockedBranch();
        cancellation();
        largeCompressedPlan();
        randomWitnesses();
        System.out.println("Graph pipeline: " + checks + " assertions passed");
    }

    /** Can also run against the previous production JAR to compare identical simulated machines. */
    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) ring(4, 20).finish();
        for (int seeds : new int[] { 1, 2, 4, 8 }) {
            var machines = ring(seeds, 100);
            long start = System.nanoTime();
            machines.finish();
            System.out.printf(Locale.ROOT, "Pipeline benchmark seeds=%d amount=%d ticks=%d pushes=%d overlaps=%d runtime_ms=%.3f wall_ms=%.3f%n",
                    seeds, seeds * 100, machines.tick, machines.pushes, machines.overlaps, machines.nanos / 1e6, (System.nanoTime() - start) / 1e6);
        }
    }

    private static Machines ring(int seeds, int rounds) {
        var a = recipe("a", Map.of("C", 1L, "R", 1L), Map.of("I", 1L));
        var b = recipe("b", Map.of("I", 1L), Map.of("J", 1L));
        var c = recipe("c", Map.of("J", 1L), Map.of("C", 1L, "P", 1L));
        var body = new PlanStep.Sequence(List.of(new PlanStep.Batch("a", seeds), new PlanStep.Batch("b", seeds), new PlanStep.Batch("c", seeds)));
        return new Machines(plan(List.of(a, b, c), new PlanStep.Repeat(body, rounds), seeds * (long) rounds,
                Map.of("C", (long) seeds, "R", seeds * (long) rounds), Map.of("C", (long) seeds)), 741);
    }

    private static void partialStages() {
        var m = ring(4, 5);
        m.step();
        check(m.accepted.getOrDefault("a", 0L) > 0 && m.accepted.get("a") <= 4,
                "Real seeds bound the initial upstream dispatches, including a yielded work slice");
        for (int i = 0; i < 4; i++) m.step();
        check(m.accepted.getOrDefault("b", 0L) > 0, "Partial upstream output starts downstream before slow batches finish");
        check(m.flights.stream().anyMatch(f -> f.recipe.equals("a")), "Upstream is still processing when downstream starts");
        m.runtime = new GraphJobRuntime<>(m.runtime.snapshot());
        m.finish();
        eq(Map.of("P", 20L), m.delivered, "Pipeline delivers exactly the requested amount");
        eq(4L, m.refunded.get("C"), "All four physical restart seeds survive pipelining");
        check(m.overlaps > 0, "Different stages overlap");
    }

    private static void reservePrefix() {
        var a = recipe("a", Map.of("C", 1L), Map.of("I", 1L));
        var b = recipe("b", Map.of("I", 1L), Map.of("C", 1L));
        var c = recipe("take-seed", Map.of("C", 1L), Map.of("P", 1L));
        var m = new Machines(plan(List.of(a, b, c), new PlanStep.Sequence(List.of(
                new PlanStep.Batch("a", 1), new PlanStep.Batch("b", 1), new PlanStep.Batch("take-seed", 1))),
                1, Map.of("C", 1L), Map.of()), 92);
        m.blocked.add("a");
        for (int i = 0; i < 20; i++) m.step();
        eq(0L, m.pushes, "A later consumer must not spend the only seed needed by the earlier cycle");
        m.runtime = new GraphJobRuntime<>(m.runtime.snapshot());
        m.blocked.clear();
        m.finish();
        eq(Map.of("a", 1L, "b", 1L, "take-seed", 1L), m.accepted, "Unblocking the provider resumes the preserved witness exactly once");
    }

    private static void independentBlockedBranch() {
        var a = recipe("a", Map.of("C", 1L, "R", 1L), Map.of("C", 1L, "A", 1L));
        var b = recipe("b", Map.of("S", 1L), Map.of("B", 1L));
        var c = recipe("c", Map.of("A", 1L, "B", 1L), Map.of("P", 1L));
        var m = new Machines(plan(List.of(a, b, c), new PlanStep.Sequence(List.of(
                new PlanStep.Batch("a", 4), new PlanStep.Batch("b", 4), new PlanStep.Batch("c", 4))),
                4, Map.of("C", 2L, "R", 4L, "S", 4L), Map.of("C", 2L)), 3);
        m.blocked.add("a");
        m.step();
        eq(4L, m.accepted.get("b"), "An independent stage can run while a cyclic provider is blocked");
        eq(null, m.accepted.get("c"), "Predicted upstream output cannot feed the join");
        m.blocked.clear();
        m.finish();
    }

    private static void reserveOnlyRequiredAmount() {
        var a = recipe("a", Map.of("C", 2L), Map.of("I", 2L));
        var b = recipe("b", Map.of("I", 2L), Map.of("C", 2L));
        var c = recipe("surplus", Map.of("C", 1L), Map.of("P", 1L));
        var m = new Machines(plan(List.of(a, b, c), new PlanStep.Sequence(List.of(
                new PlanStep.Batch("a", 1), new PlanStep.Batch("b", 1), new PlanStep.Batch("surplus", 1))),
                1, Map.of("C", 3L), Map.of("C", 2L)), 93);
        m.blocked.add("a");
        m.step();
        eq(Map.of("surplus", 1L), m.accepted, "A later stage may consume the surplus above the protected prefix");
        eq(2L, m.runtime.held("C"), "Two seeds remain available to the earlier stage");
        m.blocked.clear();
        m.finish();
        eq(2L, m.refunded.get("C"), "Recovery contract survives early surplus use");
    }

    private static void prefixPeak() {
        var a = recipe("a", Map.of("C", 1L), Map.of("C", 1L, "R", 2L));
        var b = recipe("b", Map.of("R", 2L), Map.of("I", 1L));
        var c = recipe("c", Map.of("X", 1L), Map.of("R", 1L, "P", 1L));
        var m = new Machines(plan(List.of(a, b, c), new PlanStep.Sequence(List.of(
                new PlanStep.Batch("a", 1), new PlanStep.Batch("b", 1), new PlanStep.Batch("c", 1))),
                1, Map.of("C", 1L, "R", Long.MAX_VALUE - 2, "X", 1L), Map.of("C", 1L)), 11);
        m.blocked.addAll(Set.of("a", "b"));
        m.step();
        eq(0L, m.pushes, "A later byproduct must not consume the headroom needed by the earlier prefix");
        m.blocked.clear();
        m.finish();
        eq(Long.MAX_VALUE - 1, m.refunded.get("R"), "Reordered physical long-sized balances remain exact");
    }

    private static void delayedSideOutput() {
        var a = recipe("a", Map.of("R", 1L), Map.of("I", 1L, "X", 1L));
        var b = recipe("b", Map.of("I", 1L, "C", 1L), Map.of("C", 1L, "P", 1L));
        var m = new Machines(plan(List.of(a, b), new PlanStep.Sequence(List.of(new PlanStep.Batch("a", 4), new PlanStep.Batch("b", 4))),
                4, Map.of("R", 4L, "C", 1L), Map.of("C", 1L)), 40);
        m.extraDelay.put("X", 100L);
        for (int i = 0; i < 60; i++) m.step();
        eq(4L, m.accepted.get("b"), "A delayed unrelated side output does not block the funded cyclic successor");
        eq(4L, m.runtime.held("P"), "All final products can be produced before the side output returns");
        eq(4L, m.runtime.waiting("X"), "The full side output obligation is retained");
        eq(false, m.runtime.finished(), "Outstanding side output still prevents final settlement");
        m.finish();
        eq(4L, m.refunded.get("X"), "Delayed side output is received and refunded exactly once");
    }

    private static void cancellation() {
        var m = ring(4, 10);
        for (int i = 0; i < 6; i++) m.step();
        check(m.overlaps > 0, "Cancellation happens with overlapping physical stages");
        var actual = Map.copyOf(m.physical);
        long pushes = m.pushes;
        m.runtime.cancel();
        m.runtime = new GraphJobRuntime<>(m.runtime.snapshot());
        for (int i = 0; i < 100 && !m.runtime.finished(); i++) m.step();
        eq(pushes, m.pushes, "Cancellation never redispatches a prefetched step");
        eq(actual, m.refunded, "Cancellation refunds only independently recorded CPU-held material");
        eq(GraphJobRuntime.State.CANCELLED, m.runtime.state(), "Cancelled pipeline settles");
    }

    private static void largeCompressedPlan() {
        long amount = 1_000_000_000_000L;
        var r = recipe("ring", Map.of("C", 1L, "R", 1L), Map.of("C", 1L, "P", 1L));
        var m = new Machines(plan(List.of(r), new PlanStep.Repeat(new PlanStep.Batch("ring", 1), amount), amount,
                Map.of("C", 4L, "R", amount), Map.of("C", 4L)), 91);
        m.step();
        check(m.runtime.snapshot().pipeline().size() <= 32, "Trillion-run repetition retains only a bounded window");
        m.runtime = new GraphJobRuntime<>(m.runtime.snapshot());
        eq(amount - m.pushes, m.runtime.pendingRuns().get("ring"), "Compressed pending counts stay exact beyond int");
        m.runtime.cancel();
        m.step();
        eq(amount - m.pushes, m.refunded.get("R"), "Cancelling a trillion-run witness does not expand it");
    }

    private static void randomWitnesses() {
        Random random = new Random(731911);
        for (int trial = 0; trial < 200; trial++) {
            Map<String, Long> initial = Map.of("A", 8L, "B", 8L, "C", 8L);
            Map<String, Long> balance = new LinkedHashMap<>(initial);
            List<GraphRecipe<String>> recipes = new ArrayList<>();
            List<PlanStep> steps = new ArrayList<>();
            String[] keys = { "A", "B", "C" };
            for (int index = 0; index < 48; index++) {
                String input;
                do { input = keys[random.nextInt(keys.length)]; } while (balance.getOrDefault(input, 0L) == 0);
                long quantity = 1 + random.nextInt((int) Math.min(3, balance.get(input)));
                long runs = 1 + random.nextInt((int) Math.min(3, balance.get(input) / quantity));
                String output = keys[random.nextInt(keys.length)];
                long produced = 1 + random.nextInt(4);
                var recipe = recipe("r" + index, Map.of(input, quantity), Map.of(output, produced));
                recipes.add(recipe);
                steps.add(new PlanStep.Batch(recipe.id(), runs));
                balance.merge(input, -quantity * runs, Long::sum);
                balance.merge(output, produced * runs, Long::sum);
            }
            String input = balance.entrySet().stream().filter(e -> e.getValue() > 0).findFirst().orElseThrow().getKey();
            recipes.add(recipe("final", Map.of(input, 1L), Map.of("P", 1L)));
            steps.add(new PlanStep.Batch("final", 1));
            balance.merge(input, -1L, Long::sum);
            var m = new Machines(plan(recipes, new PlanStep.Sequence(steps), 1, initial, Map.of()), trial);
            m.finish();
            balance.values().removeIf(v -> v == 0);
            eq(balance, m.refunded, "Random witness independent final material balance " + trial);
        }
    }

    private static GraphRecipe<String> recipe(String id, Map<String, Long> inputs, Map<String, Long> outputs) {
        return new GraphRecipe<>(id, id, inputs.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), outputs);
    }

    private static GraphPlan<String> plan(List<GraphRecipe<String>> recipes, PlanStep steps, long amount,
                                          Map<String, Long> initial, Map<String, Long> seeds) {
        Map<String, GraphRecipe<String>> indexed = new LinkedHashMap<>();
        recipes.forEach(recipe -> indexed.put(recipe.id(), recipe));
        return new GraphPlan<>("P", amount, !seeds.isEmpty(), steps, indexed, initial, seeds, Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
    }

    private record Flight(String recipe, long due, Map<String, Long> outputs) {}

    private static final class Machines implements GraphJobRuntime.Adapter<String> {

        GraphJobRuntime<String> runtime;
        final Map<String, Long> physical = new LinkedHashMap<>(), accepted = new LinkedHashMap<>();
        final Map<String, Long> delivered = new LinkedHashMap<>(), refunded = new LinkedHashMap<>();
        final List<Flight> flights = new ArrayList<>();
        final Set<String> blocked = new HashSet<>();
        final Map<String, Long> extraDelay = new HashMap<>();
        final Random random;
        long tick, pushes, overlaps, nanos;

        Machines(GraphPlan<String> plan, long seed) {
            runtime = new GraphJobRuntime<>(plan, plan.initial(), Map.of());
            physical.putAll(plan.initial());
            random = new Random(seed);
        }

        void step() {
            Collections.shuffle(flights, random);
            for (var iterator = flights.iterator(); iterator.hasNext();) {
                var flight = iterator.next();
                if (flight.due > tick) continue;
                flight.outputs.forEach((key, count) -> {
                    long received = runtime.accept(key, count, false);
                    if (received != 0) physical.merge(key, received, Math::addExact);
                    if (runtime.state() != GraphJobRuntime.State.CANCELLING && runtime.state() != GraphJobRuntime.State.CANCELLED)
                        eq(count, received, "Every declared physical output is accepted once");
                });
                iterator.remove();
            }
            long start = System.nanoTime();
            runtime.tick(this, tick++, 8);
            nanos += System.nanoTime() - start;
            physical.values().removeIf(v -> v == 0);
            eq(physical, runtime.owned(), "Independent physical ledger agrees with runtime");
            if (random.nextInt(7) == 0) runtime = new GraphJobRuntime<>(runtime.snapshot());
        }

        void finish() {
            for (int i = 0; i < 30_000 && !runtime.finished(); i++) step();
            check(runtime.finished(), "Delayed machines eventually finish: " + runtime.reason() + " " + runtime.pendingRuns());
            eq(runtime.plan().patternTimes(), accepted, "Every planned operation is dispatched exactly once");
            eq(Map.of("P", runtime.plan().amount()), delivered, "Exact target delivery");
            check(physical.isEmpty(), "Completed CPU has no unrefunded physical material");
        }

        @Override
        public long capacity(GraphRecipe<String> recipe, long requested) {
            return blocked.contains(recipe.id()) ? 0 : Math.min(1, requested);
        }

        @Override
        public GraphJobRuntime.Outcome push(GraphRecipe<String> recipe, long runs, Map<String, Long> inputs) {
            inputs.forEach((key, count) -> debit(key, count));
            if (flights.stream().anyMatch(f -> !f.recipe.equals(recipe.id()))) overlaps++;
            long ordinal = accepted.merge(recipe.id(), runs, Math::addExact);
            long delay = recipe.id().equals("a") ? (ordinal % 2 == 0 ? 12 : 1) : 3;
            Map<String, Long> outputs = new LinkedHashMap<>();
            recipe.outputs().forEach((key, count) -> outputs.put(key, Math.multiplyExact(count, runs)));
            for (var delayed : extraDelay.entrySet()) {
                Long amount = outputs.remove(delayed.getKey());
                if (amount != null) flights.add(new Flight(recipe.id(), tick + delay + delayed.getValue(), Map.of(delayed.getKey(), amount)));
            }
            if (!outputs.isEmpty()) flights.add(new Flight(recipe.id(), tick + delay, outputs));
            pushes++;
            return GraphJobRuntime.Outcome.ACCEPTED;
        }

        private void debit(String key, long count) {
            check(physical.getOrDefault(key, 0L) >= count, "No spending predicted or already consumed input " + key);
            physical.merge(key, -count, Long::sum);
        }

        @Override
        public long deliver(String key, long amount) {
            debit(key, amount);
            delivered.merge(key, amount, Math::addExact);
            return amount;
        }

        @Override
        public long refund(String key, long amount) {
            debit(key, amount);
            refunded.merge(key, amount, Math::addExact);
            return amount;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void eq(Object expected, Object actual, String message) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(message + ": " + expected + " != " + actual);
    }
}
