package org.gtlcore.gtlcore.integration.ae2.throughput;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.ConfigHolder;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class ThroughputMonitorDebugLogger {

    private static final Path LOG_PATH = Path.of("logs", "gtlcore-throughput-monitor.log");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String FIELD_SEPARATOR = " | ";
    private static final int MAX_TRANSACTION_DETAILS_PER_SESSION = 512;
    private static final int MAX_IGNORED_DETAILS_PER_SESSION = 512;
    private static final AtomicBoolean FAILURE_REPORTED = new AtomicBoolean();
    private static final AtomicInteger TRANSACTION_DETAILS = new AtomicInteger();
    private static final AtomicInteger IGNORED_DETAILS = new AtomicInteger();
    private static boolean sessionStarted;

    private ThroughputMonitorDebugLogger() {}

    static void writeRegister(MEStorage storage, ThroughputMonitorStorageTracker.Listener listener) {
        write("REGISTER", "storage=" + identity(storage), listenerFields(listener));
    }

    static void writeUnregister(ThroughputMonitorStorageTracker.Listener listener) {
        write("UNREGISTER", listenerFields(listener));
    }

    static void writeTransaction(MEStorage storage, AEKey key, long amountDelta, long tick, int matchedListeners) {
        if (!shouldWriteDetail(TRANSACTION_DETAILS, MAX_TRANSACTION_DETAILS_PER_SESSION, "TX_SUPPRESSED")) {
            return;
        }
        write(
                "TX",
                "tick=" + tick,
                "storage=" + identity(storage),
                "key=" + sanitize(key),
                "delta=" + amountDelta,
                "matchedListeners=" + matchedListeners);
    }

    static void writeIgnoredTransaction(MEStorage storage, AEKey key, long amountDelta, long tick, String reason) {
        if (!shouldWriteDetail(IGNORED_DETAILS, MAX_IGNORED_DETAILS_PER_SESSION, "TX_IGNORED_SUPPRESSED")) {
            return;
        }
        write(
                "TX_IGNORED",
                "tick=" + tick,
                "reason=" + sanitize(reason),
                "storage=" + identity(storage),
                "key=" + sanitize(key),
                "delta=" + amountDelta);
    }

    static void writeVisibleStorageRefresh(MEStorage storage, AEKey key, long tick, long topologyVersion, int linkedStorages) {
        write(
                "VISIBLE_REFRESH",
                "tick=" + tick,
                "storage=" + identity(storage),
                "key=" + sanitize(key),
                "topologyVersion=" + topologyVersion,
                "linkedStorages=" + linkedStorages);
    }

    static void writeState(String event, AEKey key, long tick) {
        write(event, "tick=" + tick, "key=" + sanitize(key));
    }

    static void writeDisplay(
                             AEKey key,
                             METhroughputMonitorPart.WorkRoutineView routine,
                             long tick,
                             ThroughputCache.ThroughputSample sample,
                             double reportedValue) {
        write(
                "DISPLAY",
                "tick=" + tick,
                "key=" + sanitize(key),
                "routine=" + routine.name(),
                "sampleWindowSeconds=" + routine.sampleWindowSeconds(),
                "displayTicks=" + routine.displayTicks(),
                "insertedPerTick=" + sample.insertedPerTick(),
                "extractedPerTick=" + sample.extractedPerTick(),
                "reported=" + reportedValue);
    }

    private static String listenerFields(ThroughputMonitorStorageTracker.Listener listener) {
        return "listener=" + identity(listener) + FIELD_SEPARATOR + "trackedKey=" + sanitize(listener.getTrackedKey());
    }

    private static void write(String event, String... fields) {
        if (!isEnabled()) {
            return;
        }

        StringBuilder builder = new StringBuilder(256);
        builder.append("timestamp=").append(TIMESTAMP_FORMAT.format(OffsetDateTime.now()));
        builder.append(FIELD_SEPARATOR).append(event);
        builder.append(FIELD_SEPARATOR).append("thread=").append(sanitize(Thread.currentThread().getName()));
        for (String field : fields) {
            if (field == null || field.isEmpty()) continue;
            builder.append(FIELD_SEPARATOR).append(field);
        }
        writeSafely(builder.toString());
    }

    private static boolean isEnabled() {
        ConfigHolder config = ConfigHolder.INSTANCE;
        return config != null && config.ae2ThroughputMonitorLogEnabled;
    }

    private static boolean shouldWriteDetail(AtomicInteger counter, int limit, String suppressedEvent) {
        int detail = counter.incrementAndGet();
        if (detail <= limit) {
            return true;
        }
        if (detail == limit + 1) {
            write(suppressedEvent, "limit=" + limit);
        }
        return false;
    }

    private static String identity(Object value) {
        if (value == null) {
            return "null";
        }
        return sanitize(value.getClass().getName()) + "@" + Integer.toHexString(System.identityHashCode(value));
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
            if (FAILURE_REPORTED.compareAndSet(false, true)) {
                GTLCore.LOGGER.warn("Failed to write ME throughput monitor debug log", e);
            }
        }
    }

    private static synchronized void writeLine(String line) throws IOException {
        Path parent = LOG_PATH.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!sessionStarted) {
            Files.writeString(
                    LOG_PATH,
                    System.lineSeparator() + "=== GTLCore ME Throughput Monitor Debug Session " + TIMESTAMP_FORMAT.format(OffsetDateTime.now()) + " ===" + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            sessionStarted = true;
        }
        Files.writeString(
                LOG_PATH,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
}
