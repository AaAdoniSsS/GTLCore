package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.GTCEu;

import java.util.Locale;

/** Explicit preview scope: real-world structure checks keep the existing search path. */
public final class PreviewMatcherTiming implements AutoCloseable {

    private static final ThreadLocal<PreviewMatcherTiming> ACTIVE = new ThreadLocal<>();
    private static final boolean FIXED_ENABLED = !Boolean.getBoolean("gtlcore.preview.disableFixedMatcher");
    private static final boolean INCREMENTAL_ENABLED = !Boolean.getBoolean("gtlcore.preview.disableIncrementalMatcher");
    private final PreviewMatcherTiming previous;
    private final String identity;
    private final Object state;
    private final long start = System.nanoTime();
    private long slices;
    private long replayedSlices;
    private int replays;
    private int fixedAttempts;
    private int incrementalAttempts;
    private int searchAttempts;
    private boolean matched;
    private boolean completed;
    private long elapsedNanos;

    private PreviewMatcherTiming(String identity, Object state) {
        this.identity = identity;
        this.state = state;
        previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static PreviewMatcherTiming begin(String identity, Object state) {
        return new PreviewMatcherTiming(identity, state);
    }

    public static boolean useFixedLayout(Object state) {
        var timing = ACTIVE.get();
        return FIXED_ENABLED && timing != null && timing.state == state;
    }

    public static boolean useIncrementalSearch(Object state) {
        var timing = ACTIVE.get();
        return INCREMENTAL_ENABLED && timing != null && timing.state == state;
    }

    public static void incrementalAttempt() {
        var timing = ACTIVE.get();
        if (timing != null) timing.incrementalAttempts++;
    }

    public static void attempt(boolean fixed) {
        var timing = ACTIVE.get();
        if (timing != null) {
            if (fixed) timing.fixedAttempts++;
            else timing.searchAttempts++;
        }
    }

    public static void slice() {
        var timing = ACTIVE.get();
        if (timing != null) timing.slices++;
    }

    public static void replay() {
        var timing = ACTIVE.get();
        if (timing != null) timing.replays++;
    }

    public static void replayedSlice() {
        var timing = ACTIVE.get();
        if (timing != null) timing.replayedSlices++;
    }

    public void result(boolean matched) {
        elapsedNanos = System.nanoTime() - start;
        this.matched = matched;
        completed = true;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    @Override
    public void close() {
        if (!completed) elapsedNanos = System.nanoTime() - start;
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
        GTCEu.LOGGER.info("[Preview Matcher] id={} result={} fixed_enabled={} incremental_enabled={} " +
                "fixed_attempts={} incremental_attempts={} search_attempts={} " +
                "slice_calls={} prefix_replays={} replayed_slices={} match_ms={}",
                identity, completed ? Boolean.toString(matched) : "exception", FIXED_ENABLED, INCREMENTAL_ENABLED,
                fixedAttempts, incrementalAttempts, searchAttempts, slices, replays, replayedSlices,
                String.format(Locale.ROOT, "%.3f", elapsedNanos / 1_000_000.0));
    }
}
