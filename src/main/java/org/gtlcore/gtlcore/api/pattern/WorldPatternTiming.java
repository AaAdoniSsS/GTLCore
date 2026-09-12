package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional world matcher diagnostics, enabled with {@code -Dgtlcore.worldPatternTimer.enabled=true}.
 * Never opens a preview scope or selects a different matcher.
 */
public final class WorldPatternTiming implements AutoCloseable {

    private static final boolean ENABLED = Boolean.getBoolean("gtlcore.worldPatternTimer.enabled") &&
            !Boolean.getBoolean("gtlcore.worldPatternTimer.disabled");
    private static final String FOCUS = System.getProperty("gtlcore.worldPatternTimer.focus", "subspace");
    private static final long SLOW_NANOS = 100_000_000L;
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ThreadLocal<WorldPatternTiming> CHECK = new ThreadLocal<>();
    private static final ThreadLocal<Matcher> MATCHER = new ThreadLocal<>();

    private final WorldPatternTiming previous;
    private final MultiblockState state;
    private final long sequence = SEQUENCE.incrementAndGet();
    private final String identity;
    private final String dimension;
    private final String position;
    private final String thread = Thread.currentThread().getName();
    private final boolean serverThread;
    private final String source;
    private final boolean focused;
    private final long start;
    private long lockNanos;
    private String lockAcquired = "not_requested";
    private long matcherNanos;
    private int matcherCalls;
    private int unloadedAttempts;
    private String firstFailureError = "none";
    private String firstFailurePos = "none";
    private String firstFailureFacing = "none";
    private boolean firstFailureFlipped;
    private int startAttempts;
    private long slices;
    private long replays;
    private long replayedSlices;
    private String algorithm = "none";
    private String lastFacing = "none";
    private boolean lastFlipped;
    private String result = "exception";

    private WorldPatternTiming(MultiblockState state, IMultiController controller, String source) {
        this.state = state;
        this.source = source;
        identity = controller == null ? "unknown" : controller.self().getDefinition().getId().toString();
        ServerLevel level = (ServerLevel) state.world;
        dimension = level.dimension().location().toString();
        position = state.controllerPos.getX() + "," + state.controllerPos.getY() + "," + state.controllerPos.getZ();
        serverThread = level.getServer().isSameThread();
        focused = FOCUS.isEmpty() || identity.contains(FOCUS);
        previous = CHECK.get();
        CHECK.set(this);
        if (focused) {
            GTCEu.LOGGER.info("[World Pattern] START timer_version=2 seq={} id={} dim={} pos={} " +
                    "thread=\"{}\" server_thread={} source={}",
                    sequence, identity, dimension, position, thread, serverThread, source);
        }
        // Exclude START log formatting/output from the reported check time.
        start = System.nanoTime();
    }

    public static WorldPatternTiming beginCheck(IMultiController controller, String source) {
        if (!ENABLED || !(controller.self().getLevel() instanceof ServerLevel)) return null;
        return new WorldPatternTiming(controller.getMultiblockState(), controller, source);
    }

    public static Matcher beginMatcher(MultiblockState state, Direction facing, boolean flipped) {
        if (!ENABLED || !(state.world instanceof ServerLevel)) return null;
        WorldPatternTiming owner = CHECK.get();
        boolean standalone = owner == null || owner.state != state;
        if (standalone) owner = new WorldPatternTiming(state, state.lastController, "direct_orientation");
        return new Matcher(owner, state, facing, flipped, standalone);
    }

    public void lockResult(long lockStart, boolean acquired) {
        lockNanos += System.nanoTime() - lockStart;
        lockAcquired = Boolean.toString(acquired);
    }

    public void result(boolean matched) {
        result = "false".equals(lockAcquired) ? "lock_busy" : Boolean.toString(matched);
    }

    public static void attempt(MultiblockState state, String algorithm) {
        if (!ENABLED) return;
        Matcher matcher = MATCHER.get();
        if (matcher != null && matcher.state == state) {
            matcher.owner.algorithm = algorithm;
            matcher.owner.startAttempts++;
        }
    }

    public static void slice(MultiblockState state) {
        if (!ENABLED) return;
        Matcher matcher = MATCHER.get();
        if (matcher != null && matcher.state == state) matcher.owner.slices++;
    }

    public static void replay(MultiblockState state) {
        if (!ENABLED) return;
        Matcher matcher = MATCHER.get();
        if (matcher != null && matcher.state == state) matcher.owner.replays++;
    }

    public static void replayedSlice(MultiblockState state) {
        if (!ENABLED) return;
        Matcher matcher = MATCHER.get();
        if (matcher != null && matcher.state == state) matcher.owner.replayedSlices++;
    }

    @Override
    public void close() {
        long elapsed = System.nanoTime() - start;
        if (previous == null) CHECK.remove();
        else CHECK.set(previous);
        // Keep incomplete small machines from printing every second. Focused checks always log.
        if (focused || elapsed >= SLOW_NANOS || "true".equals(result) || "exception".equals(result)) {
            GTCEu.LOGGER.info("[World Pattern] END timer_version=2 seq={} id={} dim={} pos={} " +
                    "thread=\"{}\" server_thread={} source={} result={} lock_acquired={} " +
                    "check_total_ms={} lock_wait_ms={} matcher_ms={} matcher_calls={} " +
                    "algorithm={} start_attempts={} slice_calls={} prefix_replays={} replayed_slices={} " +
                    "last_facing={} last_flipped={} unloaded={} unloaded_attempts={} " +
                    "first_failure_error={} first_failure_pos={} first_failure_facing={} first_failure_flipped={}",
                    sequence, identity, dimension, position, thread, serverThread, source, result, lockAcquired,
                    millis(elapsed), millis(lockNanos), millis(matcherNanos), matcherCalls,
                    algorithm, startAttempts, slices, replays, replayedSlices, lastFacing, lastFlipped,
                    state.error == MultiblockState.UNLOAD_ERROR, unloadedAttempts,
                    firstFailureError, firstFailurePos, firstFailureFacing, firstFailureFlipped);
        }
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static String lastVisitedPosition(MultiblockState state) {
        try {
            var pos = state.getPos();
            return pos == null ? "unknown" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
        } catch (RuntimeException ignored) {
            // GTCEu's getter dereferences its nullable position before the first update.
            return "unknown";
        }
    }

    public static final class Matcher implements AutoCloseable {

        private final WorldPatternTiming owner;
        private final MultiblockState state;
        private final Matcher previous;
        private final boolean standalone;
        private final String facing;
        private final boolean flipped;
        private final long start = System.nanoTime();
        private boolean matched;
        private boolean completed;

        private Matcher(WorldPatternTiming owner, MultiblockState state, Direction facing, boolean flipped,
                        boolean standalone) {
            this.owner = owner;
            this.state = state;
            this.standalone = standalone;
            this.facing = facing.name();
            this.flipped = flipped;
            previous = MATCHER.get();
            MATCHER.set(this);
            owner.matcherCalls++;
            owner.lastFacing = facing.name();
            owner.lastFlipped = flipped;
        }

        public void result(boolean matched) {
            this.matched = matched;
            completed = true;
        }

        @Override
        public void close() {
            owner.matcherNanos += System.nanoTime() - start;
            if (previous == null) MATCHER.remove();
            else MATCHER.set(previous);
            try {
                // Snapshot each orientation's error before the outer GT wrapper retries a flip and clears it.
                boolean unloaded = completed && state.error == MultiblockState.UNLOAD_ERROR;
                if (unloaded) owner.unloadedAttempts++;
                if ((!completed || !matched) && "none".equals(owner.firstFailureError)) {
                    owner.firstFailureError = !completed ? "exception" : unloaded ? "unloaded" :
                            state.error == null ? "predicate_rejected" : state.error.getClass().getSimpleName();
                    owner.firstFailurePos = lastVisitedPosition(state);
                    owner.firstFailureFacing = facing;
                    owner.firstFailureFlipped = flipped;
                }
            } finally {
                if (standalone) {
                    if (completed) owner.result(matched);
                    owner.close();
                }
            }
        }
    }
}
