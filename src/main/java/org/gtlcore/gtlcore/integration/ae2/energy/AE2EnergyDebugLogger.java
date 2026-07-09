package org.gtlcore.gtlcore.integration.ae2.energy;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.ConfigHolder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AE2EnergyDebugLogger {

    private static final Path LOG_PATH = Path.of("logs", "gtlcore-ae-energy.log");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String FIELD_SEPARATOR = " | ";
    private static final AtomicBoolean FAILURE_REPORTED = new AtomicBoolean();
    private static BufferedWriter writer;

    private AE2EnergyDebugLogger() {}

    public static boolean isEnabled() {
        ConfigHolder config = ConfigHolder.INSTANCE;
        return config != null && config.ae2EnergyLogEnabled;
    }

    public static void writeTick(
                                 Object grid,
                                 long tick,
                                 int nodeCount,
                                 boolean powered,
                                 double storedStart,
                                 double storedEnd,
                                 double maxStored,
                                 double idlePower,
                                 double channelPower,
                                 double avgDrain,
                                 double avgInjection,
                                 EnergyCounters counters) {
        if (!isEnabled()) {
            return;
        }

        write(
                "AE_ENERGY",
                "tick=" + tick,
                "grid=" + identity(grid),
                "nodes=" + nodeCount,
                "powered=" + powered,
                "storedStart=" + storedStart,
                "storedEnd=" + storedEnd,
                "storedDelta=" + (storedEnd - storedStart),
                "maxStored=" + maxStored,
                "idlePower=" + idlePower,
                "channelPower=" + channelPower,
                "avgDrain=" + avgDrain,
                "avgInjection=" + avgInjection,
                "extractRequest=" + counters.extractRequest,
                "extracted=" + counters.extracted,
                "simulateExtractRequest=" + counters.simulateExtractRequest,
                "simulateExtracted=" + counters.simulateExtracted,
                "providerExtractRequest=" + counters.providerExtractRequest,
                "providerExtracted=" + counters.providerExtracted,
                "providerSimExtractRequest=" + counters.providerSimExtractRequest,
                "providerSimExtracted=" + counters.providerSimExtracted,
                "injectInput=" + counters.injectInput,
                "injected=" + counters.injectInputAccepted(),
                "simulateInjectInput=" + counters.simulateInjectInput,
                "simulateInjected=" + counters.simulateInjectInputAccepted(),
                "providerInjectInput=" + counters.providerInjectInput,
                "providerInjected=" + counters.providerInjectInputAccepted(),
                "providerSimInjectInput=" + counters.providerSimInjectInput,
                "providerSimInjected=" + counters.providerSimInjectInputAccepted());
    }

    private static void write(String event, String... fields) {
        StringBuilder builder = new StringBuilder(512);
        builder.append("timestamp=").append(TIMESTAMP_FORMAT.format(OffsetDateTime.now()));
        builder.append(FIELD_SEPARATOR).append(event);
        builder.append(FIELD_SEPARATOR).append("thread=").append(sanitize(Thread.currentThread().getName()));
        for (String field : fields) {
            if (field == null || field.isEmpty()) continue;
            builder.append(FIELD_SEPARATOR).append(field);
        }
        writeSafely(builder.toString());
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
                GTLCore.LOGGER.warn("Failed to write AE2 energy debug log", e);
            }
        }
    }

    private static synchronized void writeLine(String line) throws IOException {
        if (writer == null) {
            Path parent = LOG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writer = Files.newBufferedWriter(
                    LOG_PATH,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            writer.newLine();
            writer.write("=== GTLCore AE2 Energy Debug Session " + TIMESTAMP_FORMAT.format(OffsetDateTime.now()) + " ===");
            writer.newLine();
        }
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    public static final class EnergyCounters {

        public double extractRequest;
        public double extracted;
        public double simulateExtractRequest;
        public double simulateExtracted;
        public double providerExtractRequest;
        public double providerExtracted;
        public double providerSimExtractRequest;
        public double providerSimExtracted;
        public double injectInput;
        public double injectRemainder;
        public double simulateInjectInput;
        public double simulateInjectRemainder;
        public double providerInjectInput;
        public double providerInjectRemainder;
        public double providerSimInjectInput;
        public double providerSimInjectRemainder;

        public double injectInputAccepted() {
            return accepted(injectInput, injectRemainder);
        }

        public double simulateInjectInputAccepted() {
            return accepted(simulateInjectInput, simulateInjectRemainder);
        }

        public double providerInjectInputAccepted() {
            return accepted(providerInjectInput, providerInjectRemainder);
        }

        public double providerSimInjectInputAccepted() {
            return accepted(providerSimInjectInput, providerSimInjectRemainder);
        }

        private static double accepted(double input, double remainder) {
            return Math.max(0.0D, input - remainder);
        }
    }
}
