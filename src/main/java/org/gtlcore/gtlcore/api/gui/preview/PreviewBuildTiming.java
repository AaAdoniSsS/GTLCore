package org.gtlcore.gtlcore.api.gui.preview;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Times full preview matching and formation; no result storage or world access from the timer. */
public final class PreviewBuildTiming implements AutoCloseable {

    private static final Object LOCK = new Object();
    private static final long IDLE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "GTL-Preview-Timer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static boolean phaseActive;
    private static int activeOperations;
    private static int hosts;
    private static int modules;
    private static long phaseStart;
    private static long lastEnd;
    private static long hostNanos;
    private static long moduleNanos;
    private static long matcherNanos;
    private static long formedNanos;

    private final boolean module;
    private final long start;

    private PreviewBuildTiming(boolean module, long start) {
        this.module = module;
        this.start = start;
    }

    public static PreviewBuildTiming begin(boolean module) {
        synchronized (LOCK) {
            long now = System.nanoTime();
            if (!phaseActive) {
                phaseActive = true;
                phaseStart = now;
                hosts = modules = 0;
                hostNanos = moduleNanos = matcherNanos = formedNanos = 0L;
                GTCEu.LOGGER.info("[Preview Build] START timing_version=3 persistent_cache=false");
                TIMER.schedule(PreviewBuildTiming::summarizeWhenIdle, 1, TimeUnit.SECONDS);
            }
            activeOperations++;
            return new PreviewBuildTiming(module, now);
        }
    }

    public static void recordMatcher(long nanos) {
        synchronized (LOCK) {
            matcherNanos += nanos;
        }
    }

    public static void formController(IMultiController controller) {
        long start = System.nanoTime();
        try {
            controller.onStructureFormed();
        } finally {
            synchronized (LOCK) {
                formedNanos += System.nanoTime() - start;
            }
        }
    }

    public static String identity(IMultiController controller, boolean module) {
        return controller.self().getDefinition().getId() + (module ? "#module" : "");
    }

    @Override
    public void close() {
        synchronized (LOCK) {
            lastEnd = System.nanoTime();
            if (module) {
                modules++;
                moduleNanos += lastEnd - start;
            } else {
                hosts++;
                hostNanos += lastEnd - start;
            }
            activeOperations--;
        }
    }

    private static void summarizeWhenIdle() {
        synchronized (LOCK) {
            if (activeOperations > 0 || System.nanoTime() - lastEnd < IDLE_NANOS) {
                TIMER.schedule(PreviewBuildTiming::summarizeWhenIdle, 1, TimeUnit.SECONDS);
                return;
            }
            phaseActive = false;
            long wall = lastEnd - phaseStart;
            long operations = hostNanos + moduleNanos;
            GTCEu.LOGGER.info("[Preview Build] END wall_ms={} hook_ms={} outside_hook_ms={} " +
                    "host_hook_ms={} module_hook_ms={} host_checks={} module_checks={} " +
                    "pure_matcher_ms={} formed_ms={}",
                    millis(wall), millis(operations), millis(Math.max(0L, wall - operations)),
                    millis(hostNanos), millis(moduleNanos), hosts, modules, millis(matcherNanos), millis(formedNanos));
        }
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
