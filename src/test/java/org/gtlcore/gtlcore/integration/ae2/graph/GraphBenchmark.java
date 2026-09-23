package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.*;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Reproducible planner benchmarks, excluding machine processing and Minecraft startup. */
public final class GraphBenchmark {

    private static volatile Object blackhole;

    public static void main(String[] args) {
        List<GraphRecipe<String>> chain = new ArrayList<>();
        for (int i = 1; i <= 512; i++) chain.add(recipe("r" + i, Map.of("k" + (i - 1), 1L), Map.of("k" + i, 1L)));
        measure("chain512", chain, "k512", 1_000_000, Map.of("k0", 1_000_000L));
        List<GraphRecipe<String>> diamond = new ArrayList<>();
        Map<String, Long> parts = new LinkedHashMap<>();
        diamond.add(recipe("shared", Map.of("R", 2L), Map.of("S", 1L)));
        for (int i = 0; i < 16; i++) {
            diamond.add(recipe("branch" + i, Map.of("S", 1L), Map.of("P" + i, 1L)));
            parts.put("P" + i, 1L);
        }
        diamond.add(recipe("final", parts, Map.of("T", 1L)));
        measure("diamond16", diamond, "T", 1_000_000, Map.of("R", 32_000_000L));
        measure("coupled-outputs", List.of(recipe("split", Map.of("R", 2L), Map.of("A", 1L, "B", 1L)),
                recipe("join", Map.of("A", 1L, "B", 1L), Map.of("T", 1L))), "T", 1_000_000, Map.of("R", 2_000_000L));
        measure("local-ratio-3-to-2", List.of(recipe("ab", Map.of("A", 3L, "R", 1L), Map.of("B", 2L)),
                recipe("ba", Map.of("B", 3L), Map.of("A", 5L, "P", 1L))), "P", 1_000_000, Map.of("A", 9L, "R", 1_500_000L));
        var growth = List.of(recipe("grow", Map.of("A", 1L, "R", 1L), Map.of("A", 2L)));
        var cycle = List.of(recipe("ab", Map.of("A", 1L, "R", 1L), Map.of("B", 1L)),
                recipe("ba", Map.of("B", 1L), Map.of("A", 1L, "P", 1L)));
        for (long n : new long[] { 1, 1_000_000L, 1_000_000_000_000L }) {
            measure("growth", growth, "A", n, Map.of("A", 1L, "R", n));
            measure("fixed-cycle", cycle, "P", n, Map.of("A", 1L, "R", n));
        }
        runtimeBatch();
    }

    private static void runtimeBatch() {
        var recipes = List.of(recipe("p", Map.of("R", 1L), Map.of("P", 1L)));
        for (long amount : new long[] { 1_000_000, 1_000_000_000_000L }) {
            var plan = run(new GraphCompiler<>(recipes), "P", amount, Map.of("R", amount));
            long[] nanos = new long[120];
            long dispatches = 0;
            for (int sample = -80; sample < nanos.length; sample++) {
                var runtime = new GraphJobRuntime<>(plan, plan.initial(), Map.of());
                var adapter = new GraphJobRuntime.Adapter<String>() {

                    @Override
                    public long capacity(GraphRecipe<String> recipe, long requested) {
                        return requested;
                    }

                    @Override
                    public GraphJobRuntime.Outcome push(GraphRecipe<String> recipe, long runs, Map<String, Long> inputs) {
                        runtime.accept("P", runs, false);
                        return GraphJobRuntime.Outcome.ACCEPTED;
                    }

                    @Override
                    public long deliver(String key, long count) {
                        return count;
                    }

                    @Override
                    public long refund(String key, long count) {
                        return count;
                    }
                };
                long start = System.nanoTime();
                for (int tick = 0; tick < 10 && !runtime.finished(); tick++) runtime.tick(adapter, tick, 64);
                if (!runtime.finished()) throw new AssertionError("Batch did not finish");
                if (sample >= 0) nanos[sample] = System.nanoTime() - start;
                dispatches = runtime.dispatches();
            }
            Arrays.sort(nanos);
            System.out.printf(Locale.ROOT, "runtime-sync-batch n=%d median_ms=%.4f p95_ms=%.4f dispatches=%d%n",
                    amount, nanos[60] / 1e6, nanos[113] / 1e6, dispatches);
        }
    }

    private static void measure(String label, List<GraphRecipe<String>> recipes, String target, long count, Map<String, Long> stock) {
        var compiler = new GraphCompiler<>(recipes);
        for (boolean cold : new boolean[] { true, false }) {
            for (int i = 0; i < 80; i++) run(cold ? new GraphCompiler<>(recipes) : compiler, target, count, stock);
            long[] nanos = new long[120];
            var memory = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long tid = Thread.currentThread().getId(), bytes = 0;
            GraphPlan<String> last = null;
            for (int i = 0; i < nanos.length; i++) {
                long allocated = memory.getThreadAllocatedBytes(tid), start = System.nanoTime();
                last = run(cold ? new GraphCompiler<>(recipes) : compiler, target, count, stock);
                nanos[i] = System.nanoTime() - start;
                bytes += memory.getThreadAllocatedBytes(tid) - allocated;
            }
            Arrays.sort(nanos);
            System.out.printf(Locale.ROOT, "%s n=%d cache=%s median_ms=%.4f p95_ms=%.4f alloc_bytes=%d plan_nodes=%d search_nodes=%d%n",
                    label, count, cold ? "cold" : "hot", nanos[60] / 1e6, nanos[113] / 1e6, bytes / nanos.length,
                    nodes(last.steps()), last.searchNodes());
        }
    }

    private static GraphPlan<String> run(GraphCompiler<String> compiler, String target, long count, Map<String, Long> stock) {
        var plan = new GraphPlanner<>(compiler).plan(target, count, stock, true, true, new PlanningBudget(10000, 1000000, () -> false));
        if (!plan.feasible()) throw new AssertionError(plan.result() + " " + plan.missing());
        blackhole = plan;
        return plan;
    }

    private static int nodes(PlanStep step) {
        if (step instanceof PlanStep.Batch) return 1;
        if (step instanceof PlanStep.Repeat repeat) return 1 + nodes(repeat.body());
        return 1 + ((PlanStep.Sequence) step).children().stream().mapToInt(GraphBenchmark::nodes).sum();
    }

    private static GraphRecipe<String> recipe(String id, Map<String, Long> input, Map<String, Long> output) {
        return new GraphRecipe<>(id, id, input.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), output);
    }
}
