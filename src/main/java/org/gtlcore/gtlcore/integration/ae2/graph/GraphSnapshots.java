package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;

import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;

/** Only world snapshot work uses a tick budget. Ready solver work never waits for a tick. */
@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID)
public final class GraphSnapshots {

    private static final Deque<Request> pending = new ArrayDeque<>();
    private static long spentNanos;
    private static boolean draining;

    private GraphSnapshots() {}

    public static void enqueue(GtlPatternCatalog.Capture capture, PlanningBudget budget,
                               CompletableFuture<GtlPatternCatalog.Snapshot> future, LongConsumer timing) {
        pending.addLast(new Request(capture, budget, future, timing));
        drain();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        drain();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void start(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) spentNanos = 0;
    }

    /** Share the tick allowance with immediate captures; do not impose a second tick wait on tiny orders. */
    private static void drain() {
        if (draining) return;
        if (spentNanos >= 2_000_000L) return;
        long began = System.nanoTime(), deadline = began + 2_000_000L - spentNanos;
        draining = true;
        try {
            while (!pending.isEmpty() && System.nanoTime() - deadline < 0) {
                Request request = pending.removeFirst();
                if (request.future.isDone()) continue;
                long start = System.nanoTime();
                try (var timing = request.budget.work(PlanningBudget.Phase.SNAPSHOT)) {
                    boolean complete = false;
                    // Amortize diagnostic clocks and queue operations across a
                    // bounded batch. Keep the same global deadline and check
                    // cancellation inside every capture step.
                    for (int step = 0; step < 128 && System.nanoTime() - deadline < 0; step++) {
                        if (request.capture.step(deadline)) {
                            complete = true;
                            break;
                        }
                    }
                    request.nanos += System.nanoTime() - start;
                    if (complete) {
                        request.timing.accept(request.nanos);
                        request.future.complete(request.capture.result());
                    } else pending.addLast(request);
                } catch (Throwable error) {
                    request.future.completeExceptionally(error);
                }
            }
        } finally {
            spentNanos += System.nanoTime() - began;
            draining = false;
        }
    }

    @SubscribeEvent
    public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) GtlPatternCatalog.dataReloaded();
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        for (Request request : pending) request.future.cancel(false);
        pending.clear();
        spentNanos = 0;
    }

    private static final class Request {

        final GtlPatternCatalog.Capture capture;
        final PlanningBudget budget;
        final CompletableFuture<GtlPatternCatalog.Snapshot> future;
        final LongConsumer timing;
        long nanos;

        Request(GtlPatternCatalog.Capture capture, PlanningBudget budget, CompletableFuture<GtlPatternCatalog.Snapshot> future, LongConsumer timing) {
            this.capture = capture;
            this.budget = budget;
            this.future = future;
            this.timing = timing;
        }
    }
}
