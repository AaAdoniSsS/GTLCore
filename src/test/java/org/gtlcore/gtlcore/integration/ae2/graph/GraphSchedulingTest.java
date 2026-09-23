package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic clocks and counted continuations, not multi-second timing sleeps. */
public final class GraphSchedulingTest {

    public static void run() throws Exception {
        clockLimits();
        fairnessAndCancellation();
        parallelGraphs();
        System.out.println("Graph scheduling: clocks, independent limits, fair continuations, cancellation, parallel graph/SCC equivalence passed");
    }

    private static void clockLimits() {
        var clock = new AtomicLong();
        var disabled = new PlanningBudget(0, 10, 100, () -> false, clock::get);
        clock.set(30_000_000_000L);
        disabled.check();
        check(disabled.waitingNanos() == 30_000_000_000L, "Queue clock lost");
        clock.addAndGet(30_000_000_000L);
        disabled.check();
        check(disabled.runningWallNanos() == 30_000_000_000L, "0 must disable timeout, not elapsed accounting");
        var timeout = new PlanningBudget(10, 10, 100, () -> false, clock::get);
        clock.addAndGet(1_000_000_000L);
        timeout.check();
        clock.addAndGet(10_000_000L);
        expect(PlanningBudget.Limit.TIMEOUT, timeout::check);
        var search = new PlanningBudget(0, 1, 100, () -> false, clock::get);
        search.check();
        expect(PlanningBudget.Limit.SEARCH_LIMIT, search::check);
        search.reserve(100);
        expect(PlanningBudget.Limit.MEMORY_LIMIT, () -> search.reserve(1));
        check(search.reservedBytes() == 100, "Failed reservation leaked memory");
        search.release(100);
        check(search.reservedBytes() == 0, "Reservation not released");
    }

    private static void fairnessAndCancellation() throws Exception {
        try (var scheduler = new PlanningScheduler(1, 3, 1, 1_000_000_000)) {
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicInteger progress = new AtomicInteger(), atSmall = new AtomicInteger();
            PlanningBudget budget = budget();
            var longJob = scheduler.submit(new PlanningScheduler.Work<Integer>() {

                @Override
                public boolean advance(PlanningScheduler.Slice slice) {
                    if (progress.get() == 0) {
                        started.countDown();
                        await(release);
                    }
                    while (slice.next()) if (progress.incrementAndGet() == 1000) return true;
                    return false;
                }

                @Override
                public Integer result() {
                    return progress.get();
                }
            }, budget);
            check(started.await(5, TimeUnit.SECONDS), "Worker did not start");
            var small = scheduler.submit(new PlanningScheduler.Work<Integer>() {

                @Override
                public boolean advance(PlanningScheduler.Slice slice) {
                    atSmall.set(progress.get());
                    return true;
                }

                @Override
                public Integer result() {
                    return 7;
                }
            }, budget());
            release.countDown();
            check(small.get(5, TimeUnit.SECONDS) == 7 && atSmall.get() < 1000, "Small request starved");
            check(longJob.get(5, TimeUnit.SECONDS) == 1000, "Yield lost/replayed continuation");
            check(budget.nodes() == 1000, "Work count reset or duplicated across slices");
            check(scheduler.slices() >= 1001 && scheduler.peakActive() == 1, "Pool exceeded concurrency or skipped yielding");

            CountDownLatch cancelledStarted = new CountDownLatch(1), stop = new CountDownLatch(1), observed = new CountDownLatch(1);
            var cancelled = scheduler.submit(new PlanningScheduler.Work<Integer>() {

                @Override
                public boolean advance(PlanningScheduler.Slice slice) {
                    cancelledStarted.countDown();
                    await(stop);
                    try {
                        slice.budget().check();
                    } catch (java.util.concurrent.CancellationException expected) {
                        observed.countDown();
                        throw expected;
                    }
                    return false;
                }

                @Override
                public Integer result() {
                    return 0;
                }
            }, budget());
            check(cancelledStarted.await(5, TimeUnit.SECONDS), "Cancellation test did not start");
            cancelled.cancel(false);
            stop.countDown();
            check(observed.await(5, TimeUnit.SECONDS), "Cancelled future left actual work running");
        }
        // A parent waiting for a child must free the only worker, not deadlock.
        try (var scheduler = new PlanningScheduler(1, 2, 1, 1_000_000_000)) {
            var result = scheduler.submit(new PlanningScheduler.Work<Integer>() {

                CompletableFuture<List<Integer>> child;

                @Override
                public boolean advance(PlanningScheduler.Slice slice) {
                    if (child == null) {
                        child = slice.fork(List.of(() -> 42));
                        return false;
                    }
                    return child.isDone();
                }

                @Override
                public CompletableFuture<?> waitingFor() {
                    return child;
                }

                @Override
                public Integer result() {
                    return child.join().get(0);
                }
            }, budget());
            check(result.get(5, TimeUnit.SECONDS) == 42, "Pool-internal dependency deadlocked");
        }
    }

    private static void parallelGraphs() throws Exception {
        var recipes = new ArrayList<GraphRecipe<String>>();
        var rootInputs = new java.util.LinkedHashMap<String, Long>();
        for (int i = 0; i < 600; i++) {
            rootInputs.put("X" + i, 1L);
            recipes.add(recipe("x" + i, Map.of("shared", 2L), Map.of("X" + i, 1L)));
        }
        recipes.add(recipe("root", rootInputs, Map.of("target", 1L)));
        recipes.add(recipe("shared", Map.of("cycle", 3L), Map.of("shared", 2L)));
        recipes.add(recipe("cycle", Map.of("shared", 1L), Map.of("cycle", 2L)));
        var compiler = new GraphCompiler<>(recipes);
        var reference = compiler.compile("target", Map.of(), Set.of(), budget());
        for (int threads : new int[] { 1, 4 }) {
            try (var scheduler = new PlanningScheduler(threads, 4, 7, 1_000_000_000)) {
                var budget = budget();
                budget.enableMetrics();
                var work = compiler.begin("target", Map.of(), Set.of(), budget);
                var result = scheduler.submit(work, budget).get(10, TimeUnit.SECONDS);
                check(reference.equals(result), "Parallel merge changed graph semantics/priority/SCC order");
                check(result.recipes().size() == 603, "Shared node duplicated");
                check(result.regions().stream().filter(GraphCompiler.Region::cyclic).count() == 1, "Cycle back-edge lost");
                check(result.recipes().get("x0").inputs().get("shared") == 2, "Input multiplicity lost");
                check(scheduler.peakActive() <= threads && scheduler.slices() > 10, "Graph did not resume across slices");
                var metrics = budget.metrics();
                check(metrics.peakActiveWorkers() <= threads && metrics.peakActiveWorkers() >= 1, "Per-order active worker accounting");
                check(metrics.activeNanos().get(PlanningBudget.Phase.BUILD) > 0 && metrics.activeNanos().get(PlanningBudget.Phase.ANALYSE) > 0,
                        "Build and SCC phases were not measured independently");
            }
        }
    }

    private static GraphRecipe<String> recipe(String id, Map<String, Long> inputs, Map<String, Long> outputs) {
        return new GraphRecipe<>(id, id, inputs.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), outputs);
    }

    private static PlanningBudget budget() {
        return new PlanningBudget(0, 1_000_000, () -> false);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void expect(PlanningBudget.Limit kind, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + kind);
        } catch (PlanningBudget.Exhausted exhausted) {
            check(exhausted.limit() == kind, "Wrong limit reason");
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
