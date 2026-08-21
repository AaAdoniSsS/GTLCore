package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class CraftingDispatchPerformanceLogger {

    private static final String LOGGER_NAME = "gtlcore.ae2.crafting.dispatch.performance";
    private static final String APPENDER_NAME_PREFIX = "GTLCoreCraftingDispatchPerformance-";
    private static final String MINECRAFT_LOG_DIRECTORY = "logs";
    private static final String LOG_DIRECTORY = "gtlcore";
    private static final String LOG_FILE_PREFIX = "ae2-crafting-dispatch-performance-";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final String ROLLOVER_FILE_SUFFIX = "-%i.log.gz";
    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd_HH-mm-ss-SSS";
    private static final String LOG_LAYOUT = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %msg%n";
    private static final String ROLLOVER_SIZE = "16 MB";
    private static final String ROLLOVER_FILE_COUNT = "4";
    private static final long NANOS_PER_MICROSECOND = 1_000L;

    private static final Object INITIALIZATION_LOCK = new Object();
    private static volatile Logger logger;
    private static volatile String activeAppenderName;
    private static volatile boolean initializationFailed;

    private CraftingDispatchPerformanceLogger() {}

    public static boolean isEnabled() {
        return ConfigHolder.INSTANCE != null &&
                ConfigHolder.INSTANCE.debugLogging.enableAe2CraftingDispatchPerformanceLogging;
    }

    public static boolean logIfNeeded(String cpuType, Level level, BlockPos pos, @Nullable Object jobId,
                                      long elapsedNanos, long dispatchedCalls, long parallelism,
                                      int taskKinds, int waitingKinds, int storedKinds, int activeJobs,
                                      boolean storageBlocked, @Nullable Metrics metrics,
                                      long currentTick, long lastLoggedTick) {
        ConfigHolder config = ConfigHolder.INSTANCE;
        if (config == null || !config.debugLogging.enableAe2CraftingDispatchPerformanceLogging) {
            return false;
        }

        long elapsedMicros = elapsedNanos / NANOS_PER_MICROSECOND;
        if (!storageBlocked && elapsedMicros < config.debugLogging.ae2CraftingDispatchPerformanceWarningMicros) {
            return false;
        }
        if (lastLoggedTick != Long.MIN_VALUE &&
                currentTick - lastLoggedTick < config.debugLogging.ae2CraftingDispatchPerformanceLogIntervalTicks) {
            return false;
        }

        try {
            Logger dedicatedLogger = getLogger();
            if (dedicatedLogger == null) {
                return false;
            }
            Metrics measured = metrics == null ? Metrics.EMPTY : metrics;
            dedicatedLogger.warn(
                    "[AE2 Crafting Dispatch Performance] cpuType={} dimension={} pos={} job={} elapsed={}us dispatchedCalls={} expandedOperations={} parallelism={} taskKinds={} waitingKinds={} storedKinds={} activeJobs={} storageBlocked={} providerVisits={} materialAttempts={} materialFailures={} materialTime={}us energyTime={}us pushTime={}us",
                    cpuType, level.dimension().location(), pos, jobId, elapsedMicros, dispatchedCalls,
                    measured.expandedOperations, parallelism, taskKinds, waitingKinds, storedKinds, activeJobs,
                    storageBlocked, measured.providerVisits, measured.materialAttempts, measured.materialFailures,
                    measured.materialNanos / NANOS_PER_MICROSECOND,
                    measured.energyNanos / NANOS_PER_MICROSECOND,
                    measured.pushNanos / NANOS_PER_MICROSECOND);
            return true;
        } catch (Throwable ignored) {
            disableDedicatedLogger();
            return false;
        }
    }

    private static Logger getLogger() {
        Logger currentLogger = logger;
        if (currentLogger != null && isDedicatedLoggerConfigured()) {
            return currentLogger;
        }
        if (initializationFailed) {
            return null;
        }

        synchronized (INITIALIZATION_LOCK) {
            if ((logger == null || !isDedicatedLoggerConfigured()) && !initializationFailed) {
                logger = null;
                activeAppenderName = null;
                logger = createLogger();
            }
            return logger;
        }
    }

    private static Logger createLogger() {
        Configuration configuration = null;
        RollingFileAppender appender = null;
        try {
            String timestamp = DateTimeFormatter
                    .ofPattern(TIMESTAMP_PATTERN, Locale.ROOT)
                    .format(LocalDateTime.now());
            Path logDirectory = FMLPaths.GAMEDIR.get().resolve(MINECRAFT_LOG_DIRECTORY).resolve(LOG_DIRECTORY);
            Files.createDirectories(logDirectory);
            Path logFile = logDirectory.resolve(LOG_FILE_PREFIX + timestamp + LOG_FILE_EXTENSION);
            String filePattern = logDirectory
                    .resolve(LOG_FILE_PREFIX + timestamp + ROLLOVER_FILE_SUFFIX)
                    .toString();

            if (!(LogManager.getContext(false) instanceof LoggerContext context)) {
                throw new IllegalStateException("Log4j2 core LoggerContext is unavailable");
            }

            configuration = context.getConfiguration();
            String appenderName = APPENDER_NAME_PREFIX + timestamp;
            PatternLayout layout = PatternLayout.newBuilder()
                    .withConfiguration(configuration)
                    .withCharset(StandardCharsets.UTF_8)
                    .withPattern(LOG_LAYOUT)
                    .build();
            DefaultRolloverStrategy rolloverStrategy = DefaultRolloverStrategy.newBuilder()
                    .withConfig(configuration)
                    .withMax(ROLLOVER_FILE_COUNT)
                    .build();
            appender = RollingFileAppender.newBuilder()
                    .setConfiguration(configuration)
                    .setName(appenderName)
                    .setLayout(layout)
                    .setIgnoreExceptions(false)
                    .withFileName(logFile.toString())
                    .withFilePattern(filePattern)
                    .withAppend(true)
                    .withCreateOnDemand(false)
                    .withPolicy(SizeBasedTriggeringPolicy.createPolicy(ROLLOVER_SIZE))
                    .withStrategy(rolloverStrategy)
                    .build();
            if (appender == null) {
                throw new IllegalStateException("Log4j2 did not create the crafting dispatch rolling file appender");
            }

            appender.start();
            configuration.addAppender(appender);
            configuration.removeLogger(LOGGER_NAME);
            LoggerConfig loggerConfig = new LoggerConfig(
                    LOGGER_NAME, org.apache.logging.log4j.Level.WARN, false);
            loggerConfig.addAppender(appender, org.apache.logging.log4j.Level.WARN, null);
            configuration.addLogger(LOGGER_NAME, loggerConfig);
            context.updateLoggers();

            Logger dedicatedLogger = LogManager.getLogger(LOGGER_NAME);
            activeAppenderName = appenderName;
            dedicatedLogger.warn(
                    "[AE2 Crafting Dispatch Performance] log_file={} rollover_size={} rollover_files={}",
                    logFile.toAbsolutePath(), ROLLOVER_SIZE, ROLLOVER_FILE_COUNT);
            return dedicatedLogger;
        } catch (Throwable ignored) {
            if (configuration != null) {
                configuration.removeLogger(LOGGER_NAME);
            }
            if (appender != null) {
                appender.stop();
            }
            activeAppenderName = null;
            initializationFailed = true;
            return null;
        }
    }

    private static void disableDedicatedLogger() {
        synchronized (INITIALIZATION_LOCK) {
            try {
                if (LogManager.getContext(false) instanceof LoggerContext context) {
                    Configuration configuration = context.getConfiguration();
                    LoggerConfig loggerConfig = configuration.getLoggers().get(LOGGER_NAME);
                    if (loggerConfig != null) {
                        loggerConfig.getAppenders().values().forEach(appender -> appender.stop());
                        configuration.removeLogger(LOGGER_NAME);
                        context.updateLoggers();
                    }
                }
            } catch (Throwable ignored) {
                // Performance diagnostics must never interrupt crafting execution.
            } finally {
                logger = null;
                activeAppenderName = null;
                initializationFailed = true;
            }
        }
    }

    private static boolean isDedicatedLoggerConfigured() {
        String appenderName = activeAppenderName;
        if (appenderName == null || !(LogManager.getContext(false) instanceof LoggerContext context)) {
            return false;
        }

        LoggerConfig loggerConfig = context.getConfiguration().getLoggers().get(LOGGER_NAME);
        return loggerConfig != null && !loggerConfig.isAdditive() &&
                loggerConfig.getAppenders().containsKey(appenderName);
    }

    public static final class Metrics {

        private static final Metrics EMPTY = new Metrics();

        private long providerVisits;
        private long materialAttempts;
        private long materialFailures;
        private long materialNanos;
        private long energyNanos;
        private long pushNanos;
        private long expandedOperations;

        public void recordProviderVisit() {
            this.providerVisits++;
        }

        public void recordMaterialAttempt(long elapsedNanos, boolean successful) {
            this.materialAttempts++;
            if (!successful) {
                this.materialFailures++;
            }
            this.materialNanos = NumberUtils.saturatedAdd(this.materialNanos, elapsedNanos);
        }

        public void recordMaterialWork(long elapsedNanos) {
            this.materialNanos = NumberUtils.saturatedAdd(this.materialNanos, elapsedNanos);
        }

        public void recordEnergyWork(long elapsedNanos) {
            this.energyNanos = NumberUtils.saturatedAdd(this.energyNanos, elapsedNanos);
        }

        public void recordPush(long elapsedNanos, long operations) {
            this.pushNanos = NumberUtils.saturatedAdd(this.pushNanos, elapsedNanos);
            this.expandedOperations = NumberUtils.saturatedAdd(this.expandedOperations, operations);
        }
    }
}
