package org.gtlcore.gtlcore.api.gui.preview;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Runtime bookkeeping for the all-structure persistent preview cache. */
public final class LazyPreviewRuntime {

    private static final Map<Object, CacheRef> CACHE_REFS = Collections.synchronizedMap(new IdentityHashMap<>());

    private static final AtomicInteger HOST_HITS = new AtomicInteger();
    private static final AtomicInteger HOST_MISSES = new AtomicInteger();
    private static final AtomicInteger MODULE_DIRECT = new AtomicInteger();
    private static final AtomicInteger CERTIFICATE_PROMOTIONS = new AtomicInteger();
    private static final AtomicInteger WRITES = new AtomicInteger();
    private static final AtomicLong HOST_FINGERPRINT_NANOS = new AtomicLong();
    private static final AtomicLong LEGACY_SCAN_NANOS = new AtomicLong();
    private static final AtomicLong HOST_MATCHER_NANOS = new AtomicLong();
    private static final AtomicLong MODULE_MATCHER_NANOS = new AtomicLong();
    private static final AtomicLong VERIFY_NANOS = new AtomicLong();
    private static final AtomicInteger VERIFY_PASSES = new AtomicInteger();
    private static final AtomicInteger VERIFY_FAILS = new AtomicInteger();
    private static final AtomicLong HOST_OPERATION_NANOS = new AtomicLong();
    private static final AtomicLong MODULE_OPERATION_NANOS = new AtomicLong();

    // Preview creation may be deferred past JEI registration. Summarize after five idle seconds;
    // the timer only reads counters and never accesses the game world.
    private static final AtomicBoolean WORK_PHASE_ACTIVE = new AtomicBoolean();
    private static final AtomicInteger ACTIVE_CACHE_OPS = new AtomicInteger();
    private static final AtomicLong WORK_PHASE_START_NANOS = new AtomicLong();
    private static final AtomicLong LAST_CACHE_OP_END_NANOS = new AtomicLong();
    private static final AtomicInteger WORK_PHASE_GENERATION = new AtomicInteger();
    private static final long SUMMARY_IDLE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final ScheduledExecutorService SUMMARY_TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GTL-Preview-Cache-Timer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private LazyPreviewRuntime() {}

    public static long beginHostCacheOperation() {
        return beginCacheOperation();
    }

    public static long beginModuleCacheOperation() {
        return beginCacheOperation();
    }

    public static void endHostCacheOperation(long startNanos) {
        long end = System.nanoTime();
        if (startNanos > 0L && end >= startNanos) HOST_OPERATION_NANOS.addAndGet(end - startNanos);
        endCacheOperation(end);
    }

    public static void endModuleCacheOperation(long startNanos) {
        long end = System.nanoTime();
        if (startNanos > 0L && end >= startNanos) MODULE_OPERATION_NANOS.addAndGet(end - startNanos);
        endCacheOperation(end);
    }

    private static long beginCacheOperation() {
        long now = System.nanoTime();
        boolean started = WORK_PHASE_ACTIVE.compareAndSet(false, true);
        ACTIVE_CACHE_OPS.incrementAndGet();
        if (started) {
            resetPhaseStats();
            WORK_PHASE_START_NANOS.set(now);
            LAST_CACHE_OP_END_NANOS.set(now);
            int generation = WORK_PHASE_GENERATION.incrementAndGet();
            log("multiblock preview cache work START (first preview cache operation)");
            scheduleSummaryCheck(generation);
        }
        return now;
    }

    private static void endCacheOperation(long endNanos) {
        LAST_CACHE_OP_END_NANOS.set(endNanos);
        ACTIVE_CACHE_OPS.decrementAndGet();
    }

    private static void resetPhaseStats() {
        HOST_HITS.set(0);
        HOST_MISSES.set(0);
        MODULE_DIRECT.set(0);
        CERTIFICATE_PROMOTIONS.set(0);
        WRITES.set(0);
        HOST_FINGERPRINT_NANOS.set(0L);
        LEGACY_SCAN_NANOS.set(0L);
        HOST_MATCHER_NANOS.set(0L);
        MODULE_MATCHER_NANOS.set(0L);
        VERIFY_NANOS.set(0L);
        VERIFY_PASSES.set(0);
        VERIFY_FAILS.set(0);
        HOST_OPERATION_NANOS.set(0L);
        MODULE_OPERATION_NANOS.set(0L);
    }

    private static void scheduleSummaryCheck(int generation) {
        SUMMARY_TIMER.schedule(() -> checkAndMaybeFinishPhase(generation), 1L, TimeUnit.SECONDS);
    }

    private static void checkAndMaybeFinishPhase(int generation) {
        if (generation != WORK_PHASE_GENERATION.get() || !WORK_PHASE_ACTIVE.get()) return;
        long now = System.nanoTime();
        long lastEnd = LAST_CACHE_OP_END_NANOS.get();
        if (ACTIVE_CACHE_OPS.get() == 0 && lastEnd > 0L && now - lastEnd >= SUMMARY_IDLE_NANOS) {
            if (WORK_PHASE_ACTIVE.compareAndSet(true, false)) {
                printPhaseSummary(lastEnd);
            }
            return;
        }
        scheduleSummaryCheck(generation);
    }

    private static void printPhaseSummary(long phaseEndNanos) {
        long start = WORK_PHASE_START_NANOS.get();
        long wallNs = start > 0L && phaseEndNanos >= start ? phaseEndNanos - start : 0L;
        long hostOpNs = HOST_OPERATION_NANOS.get();
        long moduleOpNs = MODULE_OPERATION_NANOS.get();
        long hookNs = hostOpNs + moduleOpNs;
        long outsideNs = Math.max(0L, wallNs - hookNs);
        long fpNs = HOST_FINGERPRINT_NANOS.get();
        long matcherNs = HOST_MATCHER_NANOS.get() + MODULE_MATCHER_NANOS.get();
        log("multiblock preview cache work END wall_ms=" + nanosToMillis3(wallNs) +
                " hook_ms=" + nanosToMillis3(hookNs) +
                " outside_hook_ms=" + nanosToMillis3(outsideNs) +
                " host_hook_ms=" + nanosToMillis3(hostOpNs) +
                " module_hook_ms=" + nanosToMillis3(moduleOpNs) +
                " host_hit=" + HOST_HITS.get() +
                " host_miss=" + HOST_MISSES.get() +
                " module_direct=" + MODULE_DIRECT.get() +
                " promotions=" + CERTIFICATE_PROMOTIONS.get() +
                " writes=" + WRITES.get() +
                " structural_fp_ms=" + nanosToMillis3(fpNs) +
                " legacy_scan_ms=" + nanosToMillis3(LEGACY_SCAN_NANOS.get()) +
                " verify_ms=" + nanosToMillis3(VERIFY_NANOS.get()) +
                " verify_pass=" + VERIFY_PASSES.get() +
                " verify_fail=" + VERIFY_FAILS.get() +
                " matcher_ms=" + nanosToMillis3(matcherNs));
    }

    public static void recordHostFingerprintNanos(long nanos) {
        if (nanos > 0) HOST_FINGERPRINT_NANOS.addAndGet(nanos);
    }

    public static void recordLegacyScanNanos(long nanos) {
        if (nanos > 0) LEGACY_SCAN_NANOS.addAndGet(nanos);
    }

    public static void certificatePromotion(String id, String sourceNamespace) {
        int n = CERTIFICATE_PROMOTIONS.incrementAndGet();
        if (n <= 12 || n % 50 == 0) {
            log("certificate promotion #" + n + " source=" + sourceNamespace + " id=" + id);
        }
    }

    public static void recordVerification(long nanos, boolean passed) {
        if (nanos > 0) VERIFY_NANOS.addAndGet(nanos);
        if (passed) VERIFY_PASSES.incrementAndGet();
        else VERIFY_FAILS.incrementAndGet();
    }

    private static String nanosToMillis3(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    public static String controllerId(IMultiController controller) {
        return controller.self().getDefinition().getId().toString();
    }

    public static String hostCacheIdentity(IMultiController controller) {
        return controllerId(controller);
    }

    public static String moduleCacheIdentity(IMultiController controller) {
        return controllerId(controller) + "#module";
    }

    public static void moduleDirect(String id, long nanos) {
        int n = MODULE_DIRECT.incrementAndGet();
        if (nanos > 0) MODULE_MATCHER_NANOS.addAndGet(nanos);
        if (n <= 12 || n % 50 == 0) log("module direct matcher #" + n + ": " + id);
    }

    public static void rememberCacheRef(IMultiController controller, String identity, byte[] fingerprint) {
        if (controller == null || identity == null) return;
        CACHE_REFS.put(controller, new CacheRef(identity, fingerprint == null ? null : fingerprint.clone()));
    }

    public static CacheRef getCacheRef(IMultiController controller) {
        CacheRef ref = controller == null ? null : CACHE_REFS.get(controller);
        return ref == null ? null : new CacheRef(ref.identity, ref.fingerprint == null ? null : ref.fingerprint.clone());
    }

    public static boolean replayRemembered(IMultiController controller) {
        if (controller == null) return false;
        if (PersistentStructureCache.tryReplaySession(controller)) return true;
        CacheRef ref = getCacheRef(controller);
        return ref != null && ref.fingerprint != null &&
                PersistentStructureCache.tryReplay(controller, ref.identity, ref.fingerprint);
    }

    public static void hostHit(String id) {
        int n = HOST_HITS.incrementAndGet();
        if (n <= 12 || n % 50 == 0) log("host cache HIT #" + n + ": " + id);
    }

    public static void hostMiss(String id, long millis) {
        int n = HOST_MISSES.incrementAndGet();
        if (millis > 0) HOST_MATCHER_NANOS.addAndGet(millis * 1_000_000L);
        log("host cache MISS/computed #" + n + " in " + millis + " ms: " + id);
    }

    public static void cacheWrite(String id) {
        int n = WRITES.incrementAndGet();
        if (n <= 12 || n % 50 == 0) log("persistent cache write count=" + n + " latest=" + id);
    }

    public static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    public static Method findMethodByNameAndArity(Class<?> type, String name, int arity) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
            }
        }
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        return null;
    }

    public static void log(String message) {
        GTCEu.LOGGER.info("[Preview Cache] {}", message);
    }

    public static final class CacheRef {

        public final String identity;
        public final byte[] fingerprint;

        CacheRef(String identity, byte[] fingerprint) {
            this.identity = identity;
            this.fingerprint = fingerprint;
        }

        @Override
        public String toString() {
            return identity + "/" + (fingerprint == null ? "<session>" : PersistentStructureCache.hexShort(fingerprint));
        }
    }
}
