package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.AE2CalculationMode;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.world.level.Level;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class AE2CraftingCalculationLogger {

    private static final Path LOG_DIRECTORY = Path.of("logs");
    private static final String LOG_FILE_PREFIX = "gtlcore-ae2-crafting";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final DateTimeFormatter LOG_FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final DateTimeFormatter LOG_ENTRY_TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Path LOG_PATH = LOG_DIRECTORY.resolve(
            LOG_FILE_PREFIX + "-" + LOG_FILE_TIMESTAMP_FORMAT.format(OffsetDateTime.now()) + LOG_FILE_EXTENSION);
    private static final String FIELD_SEPARATOR = " | ";
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private AE2CraftingCalculationLogger() {}

    public static long nextId() {
        return NEXT_ID.incrementAndGet();
    }

    public static void writeStart(long id, AE2CalculationMode mode, AEKey output, long requestedAmount, Level level) {
        writeSafely(fields(
                "START",
                "id=" + id,
                "mode=" + mode,
                "level=" + sanitize(level.dimension().location().toString()),
                "output=" + sanitize(output),
                "amount=" + requestedAmount));
    }

    public static void writeSuccess(long id, long startedNanos, String source, AE2CalculationMode mode, AEKey output,
                                    long requestedAmount, Level level, ICraftingPlan plan, Counters counters) {
        long elapsedMicros = elapsedMicros(startedNanos);
        CounterSummary used = summarize(plan.usedItems());
        CounterSummary emitted = summarize(plan.emittedItems());
        CounterSummary missing = summarize(plan.missingItems());
        PatternSummary patterns = summarizePatterns(plan.patternTimes());

        writeSafely(fields(
                "END",
                "id=" + id,
                "status=success",
                "source=" + sanitize(source),
                "elapsedMicros=" + elapsedMicros,
                "elapsedMillis=" + elapsedMicros / 1000,
                "mode=" + mode,
                "level=" + sanitize(level.dimension().location().toString()),
                "output=" + sanitize(output),
                "amount=" + requestedAmount,
                "finalOutput=" + sanitize(plan.finalOutput()),
                "simulation=" + plan.simulation(),
                "multiplePaths=" + plan.multiplePaths(),
                "bytes=" + plan.bytes(),
                "patterns=" + patterns.patterns,
                "patternTimes=" + patterns.times,
                "usedKeys=" + used.keys,
                "usedAmount=" + used.amount,
                "emittedKeys=" + emitted.keys,
                "emittedAmount=" + emitted.amount,
                "missingKeys=" + missing.keys,
                "missingAmount=" + missing.amount,
                counters.snapshot().toLogFields()));
    }

    public static void writeFailure(long id, long startedNanos, AE2CalculationMode mode, AEKey output,
                                    long requestedAmount, Level level, KeyCounter missing, Throwable error,
                                    Counters counters) {
        long elapsedMicros = elapsedMicros(startedNanos);
        CounterSummary missingSummary = summarize(missing);

        writeSafely(fields(
                "END",
                "id=" + id,
                "status=failure",
                "elapsedMicros=" + elapsedMicros,
                "elapsedMillis=" + elapsedMicros / 1000,
                "mode=" + mode,
                "level=" + sanitize(level.dimension().location().toString()),
                "output=" + sanitize(output),
                "amount=" + requestedAmount,
                "missingKeys=" + missingSummary.keys,
                "missingAmount=" + missingSummary.amount,
                "error=" + sanitize(error.getClass().getName()),
                "message=" + sanitize(error.getMessage()),
                counters.snapshot().toLogFields()));
    }

    private static long elapsedMicros(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedNanos);
    }

    private static CounterSummary summarize(KeyCounter counter) {
        long amount = 0L;
        for (var entry : counter) {
            amount = NumberUtils.saturatedAdd(amount, entry.getLongValue());
        }
        return new CounterSummary(counter.size(), amount);
    }

    private static PatternSummary summarizePatterns(Map<?, Long> patternTimes) {
        long times = 0L;
        for (long value : patternTimes.values()) {
            times = NumberUtils.saturatedAdd(times, value);
        }
        return new PatternSummary(patternTimes.size(), times);
    }

    private static String fields(String event, Object... fields) {
        StringBuilder builder = new StringBuilder(256);
        builder.append("timestamp=").append(LOG_ENTRY_TIMESTAMP_FORMAT.format(OffsetDateTime.now()));
        builder.append(FIELD_SEPARATOR).append(event);
        for (Object field : fields) {
            if (field == null) continue;
            builder.append(FIELD_SEPARATOR).append(field);
        }
        return builder.toString();
    }

    private static String sanitize(Object value) {
        if (value == null) {
            return "null";
        }
        return value.toString().replace('\r', ' ').replace('\n', ' ').replace('|', '/');
    }

    private static void writeSafely(String line) {
        try {
            writeLine(line);
        } catch (RuntimeException | IOException e) {
            GTLCore.LOGGER.warn("Failed to write AE2 crafting calculation log", e);
        }
    }

    private static synchronized void writeLine(String line) throws IOException {
        Path parent = LOG_PATH.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                LOG_PATH,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private record CounterSummary(int keys, long amount) {}

    private record PatternSummary(int patterns, long times) {}

    public static final class Counters {

        private long templateCacheHits;
        private long templateCacheMisses;
        private long templateCacheClears;
        private long nodeRequests;
        private long processRequests;
        private long limitQtyProcessRequests;
        private long processChildEdges;
        private long maxProcessTimes;
        private long templateExtractionAttempts;
        private long templateExtractionHits;
        private long templateExtractionAmount;
        private long branchSuccesses;
        private long branchFailures;
        private long branchSkips;

        public void reset() {
            this.templateCacheHits = 0;
            this.templateCacheMisses = 0;
            this.templateCacheClears = 0;
            this.nodeRequests = 0;
            this.processRequests = 0;
            this.limitQtyProcessRequests = 0;
            this.processChildEdges = 0;
            this.maxProcessTimes = 0;
            this.templateExtractionAttempts = 0;
            this.templateExtractionHits = 0;
            this.templateExtractionAmount = 0;
            this.branchSuccesses = 0;
            this.branchFailures = 0;
            this.branchSkips = 0;
        }

        public void recordTemplateCacheHit() {
            this.templateCacheHits++;
        }

        public void recordTemplateCacheMiss() {
            this.templateCacheMisses++;
        }

        public void recordTemplateCacheClear() {
            this.templateCacheClears++;
        }

        public void recordNodeRequest() {
            this.nodeRequests++;
        }

        public void recordProcessRequest(boolean limitQty, long times, int childEdges) {
            this.processRequests++;
            this.processChildEdges = NumberUtils.saturatedAdd(this.processChildEdges, childEdges);
            this.maxProcessTimes = Math.max(this.maxProcessTimes, times);
            if (limitQty) {
                this.limitQtyProcessRequests++;
            }
        }

        public void recordTemplateExtraction(long extracted) {
            this.templateExtractionAttempts++;
            if (extracted > 0) {
                this.templateExtractionHits++;
                this.templateExtractionAmount = NumberUtils.saturatedAdd(this.templateExtractionAmount, extracted);
            }
        }

        public void recordBranchSuccess() {
            this.branchSuccesses++;
        }

        public void recordBranchFailure() {
            this.branchFailures++;
        }

        public void recordBranchSkip() {
            this.branchSkips++;
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    this.templateCacheHits,
                    this.templateCacheMisses,
                    this.templateCacheClears,
                    this.nodeRequests,
                    this.processRequests,
                    this.limitQtyProcessRequests,
                    this.processChildEdges,
                    this.maxProcessTimes,
                    this.templateExtractionAttempts,
                    this.templateExtractionHits,
                    this.templateExtractionAmount,
                    this.branchSuccesses,
                    this.branchFailures,
                    this.branchSkips);
        }
    }

    private record Snapshot(
                            long templateCacheHits,
                            long templateCacheMisses,
                            long templateCacheClears,
                            long nodeRequests,
                            long processRequests,
                            long limitQtyProcessRequests,
                            long processChildEdges,
                            long maxProcessTimes,
                            long templateExtractionAttempts,
                            long templateExtractionHits,
                            long templateExtractionAmount,
                            long branchSuccesses,
                            long branchFailures,
                            long branchSkips) {

        private String toLogFields() {
            return joinFields(
                    "templateCacheHits=" + this.templateCacheHits,
                    "templateCacheMisses=" + this.templateCacheMisses,
                    "templateCacheClears=" + this.templateCacheClears,
                    "nodeRequests=" + this.nodeRequests,
                    "processRequests=" + this.processRequests,
                    "limitQtyProcessRequests=" + this.limitQtyProcessRequests,
                    "processChildEdges=" + this.processChildEdges,
                    "maxProcessTimes=" + this.maxProcessTimes,
                    "templateExtractionAttempts=" + this.templateExtractionAttempts,
                    "templateExtractionHits=" + this.templateExtractionHits,
                    "templateExtractionAmount=" + this.templateExtractionAmount,
                    "branchSuccesses=" + this.branchSuccesses,
                    "branchFailures=" + this.branchFailures,
                    "branchSkips=" + this.branchSkips);
        }
    }

    private static String joinFields(Object... fields) {
        StringBuilder builder = new StringBuilder(256);
        for (Object field : fields) {
            if (field == null) continue;
            if (!builder.isEmpty()) {
                builder.append(FIELD_SEPARATOR);
            }
            builder.append(field);
        }
        return builder.toString();
    }
}
