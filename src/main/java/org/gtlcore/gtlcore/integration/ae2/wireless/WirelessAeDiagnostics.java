package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.ConfigHolder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Server-thread diagnostics; stage durations include wall-clock pauses and may overlap. */
final class WirelessAeDiagnostics {

    private static final Map<String, long[]> STAGES = new LinkedHashMap<>();
    private static final Map<String, Long> COUNTS = new LinkedHashMap<>();
    private static final Map<String, Long> NEXT_FAILURE_LOG = new LinkedHashMap<>();
    private static final Map<String, Long> SUPPRESSED_FAILURES = new LinkedHashMap<>();
    private static final Map<String, Integer> SAMPLE_COUNTS = new HashMap<>();
    private static final Map<String, String> SAMPLE_DETAILS = new LinkedHashMap<>();
    private static long maxTick;
    private static long overBudgetTicks;
    private static long maxMember;
    private static int maxQueueWaitTicks;
    private static String slowestMember = "none";

    private WirelessAeDiagnostics() {}

    /** Bounded by internal operation names, not by the number of networks or members. */
    static void failure(String operation, Object frequency, Object member, RuntimeException error) {
        long now = System.nanoTime();
        Long next = NEXT_FAILURE_LOG.get(operation);
        if (next != null && now - next < 0) {
            SUPPRESSED_FAILURES.merge(operation, 1L, Long::sum);
            return;
        }
        long suppressed = SUPPRESSED_FAILURES.getOrDefault(operation, 0L);
        SUPPRESSED_FAILURES.remove(operation);
        NEXT_FAILURE_LOG.put(operation, now + 60_000_000_000L);
        GTLCore.LOGGER.warn("Wireless ME failure operation={} frequency={} member={} suppressed={}",
                operation, frequency, member, suppressed, error);
    }

    static void resetFailures() {
        NEXT_FAILURE_LOG.clear();
        SUPPRESSED_FAILURES.clear();
    }

    static boolean enabled() {
        return ConfigHolder.INSTANCE != null && ConfigHolder.INSTANCE.debugLogging.enableWirelessAeNetworkPerformanceLogging;
    }

    static long start() {
        return enabled() ? System.nanoTime() : 0;
    }

    static void end(String stage, long started) {
        if (started == 0) return;
        long elapsed = System.nanoTime() - started;
        long[] values = STAGES.computeIfAbsent(stage, ignored -> new long[3]);
        values[0]++;
        values[1] += elapsed;
        values[2] = Math.max(values[2], elapsed);
    }

    static <T> T measure(String stage, Supplier<T> action) {
        if (!enabled()) return action.get();
        long started = start();
        try {
            return action.get();
        } finally {
            end(stage, started);
        }
    }

    static void count(String name) {
        add(name, 1L);
    }

    static void run(String stage, Runnable action) {
        long started = start();
        try {
            action.run();
        } finally {
            end(stage, started);
        }
    }

    static void add(String name, long amount) {
        if (enabled()) COUNTS.merge(name, amount, Long::sum);
    }

    static void max(String name, long value) {
        if (enabled()) COUNTS.merge(name, value, Math::max);
    }

    static void gauge(String name, long value) {
        if (enabled()) COUNTS.put(name, value);
    }

    static void sample(String category, Object frequency, Object member, String detail) {
        if (!enabled()) return;
        int count = SAMPLE_COUNTS.getOrDefault(category, 0);
        if (count >= 8) return;
        SAMPLE_COUNTS.put(category, count + 1);
        SAMPLE_DETAILS.put(category + "_sample_" + count,
                "frequency=" + frequency + ",member=" + member + "," + detail);
    }

    static void member(long started, Object frequency, Object member) {
        if (started == 0) return;
        long elapsed = System.nanoTime() - started;
        if (elapsed > maxMember) {
            maxMember = elapsed;
            slowestMember = "frequency=" + frequency + " member=" + member;
        }
    }

    static void tick(long started, long budget) {
        if (started == 0) return;
        long elapsed = System.nanoTime() - started;
        maxTick = Math.max(maxTick, elapsed);
        if (elapsed > budget) overBudgetTicks++;
    }

    static void queueWait(int ticks) {
        if (enabled()) maxQueueWaitTicks = Math.max(maxQueueWaitTicks, ticks);
    }

    static void finishTick(long started, long budget, int tick, WirelessAeMemberScheduler scheduler, int connected) {
        if (!enabled()) return;
        tick(started, budget);
        int interval = Math.max(20, ConfigHolder.INSTANCE.debugLogging.wirelessAeNetworkPerformanceLogIntervalTicks);
        if (tick % interval == 0) {
            int retries = measure("retry_count_lookup", scheduler::retryCount);
            count("retry_count_lookups");
            run("scheduler_diagnostic_summary", scheduler::diagnostics);
            WirelessAePerformanceLogger.log(scheduler.queuedNetworks(), retries, connected);
            reset();
        }
    }

    static String summary() {
        StringBuilder text = new StringBuilder("max_tick_micros=").append(maxTick / 1000)
                .append(" over_budget_ticks=").append(overBudgetTicks)
                .append(" max_queue_wait_ticks=").append(maxQueueWaitTicks)
                .append(" max_member_micros=").append(maxMember / 1000)
                .append(" slowest_member=[").append(slowestMember).append("] counters=").append(COUNTS);
        STAGES.forEach((stage, values) -> text.append(' ').append(stage).append("=[calls=").append(values[0])
                .append(",total_micros=").append(values[1] / 1000).append(",max_micros=").append(values[2] / 1000).append(']'));
        if (!SAMPLE_DETAILS.isEmpty()) text.append(" samples=").append(SAMPLE_DETAILS);
        return text.toString();
    }

    static void reset() {
        STAGES.clear();

        COUNTS.clear();
        SAMPLE_COUNTS.clear();
        SAMPLE_DETAILS.clear();

        maxTick = 0;
        overBudgetTicks = 0;
        maxMember = 0;
        maxQueueWaitTicks = 0;
        slowestMember = "none";
    }
}
