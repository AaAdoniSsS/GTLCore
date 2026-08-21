package org.gtlcore.gtlcore.integration.ae2.crafting.transfinite;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.ConfigHolder;

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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TransfiniteComputationArrayLifecycleLogger {

    private static final String LOGGER_NAME = "gtlcore.transfinite_computation_array.lifecycle";
    private static final String APPENDER_NAME_PREFIX = "GTLCoreTransfiniteComputationArrayLifecycle-";
    private static final String MINECRAFT_LOG_DIRECTORY = "logs";
    private static final String LOG_DIRECTORY = "gtlcore";
    private static final String LOG_FILE_PREFIX = "transfinite-computation-array-lifecycle-";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final String ROLLOVER_FILE_SUFFIX = "-%i.log.gz";
    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd_HH-mm-ss-SSS";
    private static final String LOG_LAYOUT = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %msg%n";
    private static final String ROLLOVER_SIZE = "16 MB";
    private static final String ROLLOVER_FILE_COUNT = "4";
    private static final String UNKNOWN_DIMENSION = "unknown";
    private static final String UNKNOWN_GAME_TIME = "unknown";
    private static final String UNKNOWN_SIDE = "unknown";
    private static final String UNKNOWN_DURATION = "unknown";
    public static final long UNAVAILABLE_DURATION_NANOS = Long.MIN_VALUE;
    private static final long NANOS_PER_MICROSECOND = 1_000L;

    private static final Object INITIALIZATION_LOCK = new Object();
    private static volatile Logger logger;
    private static volatile String activeAppenderName;
    private static volatile boolean initializationFailed;

    private TransfiniteComputationArrayLifecycleLogger() {}

    public static boolean isEnabled() {
        return ConfigHolder.INSTANCE == null ||
                ConfigHolder.INSTANCE.debugLogging.enableTransfiniteComputationArrayLifecycleLogging;
    }

    public static void logStructureCheckStarted(Level level, BlockPos controllerPos, long periodId,
                                                boolean eager, boolean formedBefore, long queuedGameTime,
                                                long startedGameTime, long queuedNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=structure_check phase=start dimension={} controller_pos={} " +
                        "period_id={} eager={} formed_before={} queued_game_time={} started_game_time={} queued_us={}",
                dimension(level), controllerPos, periodId, eager, formedBefore, queuedGameTime, startedGameTime,
                toMicros(queuedNanos));
    }

    public static void logMachineStartupBudget(Level level, long gameTime,
                                               int normalBudget, int normalAdmitted, int normalDeferred,
                                               int aeBudget, int aeAdmitted, int aeDeferred, int critical,
                                               long timeBudgetNanos, long elapsedNanos,
                                               int normalTimeDeferred, int aeTimeDeferred) {
        info(
                "[TRANSFINITE_ARRAY] event=machine_startup_budget dimension={} game_time={} " +
                        "normal_budget={} normal_admitted={} normal_deferred={} ae_budget={} ae_admitted={} " +
                        "ae_deferred={} critical={} time_budget_ms={} elapsed_ms={} " +
                        "normal_time_deferred={} ae_time_deferred={}",
                dimension(level), gameTime, normalBudget, normalAdmitted, normalDeferred,
                aeBudget, aeAdmitted, aeDeferred, critical, timeBudgetNanos / NANOS_PER_MICROSECOND / 1000,
                elapsedNanos / NANOS_PER_MICROSECOND / 1000, normalTimeDeferred, aeTimeDeferred);
    }

    public static void logStructureCheck(Level level, BlockPos controllerPos, long periodId, boolean eager,
                                         boolean formedBefore, boolean matched, long queuedNanos,
                                         long checkNanos, long totalNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=structure_check phase=complete result={} dimension={} game_time={} " +
                        "controller_pos={} period_id={} eager={} formed_before={} queued_us={} check_us={} total_us={}",
                matched ? "matched" : "not_matched", dimension(level), gameTime(level), controllerPos, periodId, eager,
                formedBefore, toMicros(queuedNanos), toMicros(checkNanos), toMicros(totalNanos));
    }

    public static void logStructureCheckAborted(Level level, BlockPos controllerPos, long periodId,
                                                boolean eager, String reason, long queuedNanos,
                                                long totalNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=structure_check phase=complete result=aborted reason={} dimension={} " +
                        "game_time={} controller_pos={} period_id={} eager={} queued_us={} total_us={}",
                reason, dimension(level), gameTime(level), controllerPos, periodId, eager, toMicros(queuedNanos),
                toMicros(totalNanos));
    }

    public static void logStructureCheckFailure(Level level, BlockPos controllerPos, long periodId,
                                                boolean eager, long queuedNanos, long totalNanos,
                                                Throwable exception) {
        error(
                "[TRANSFINITE_ARRAY] event=structure_check phase=complete result=failed dimension={} game_time={} " +
                        "controller_pos={} period_id={} eager={} queued_us={} total_us={}",
                exception, dimension(level), gameTime(level), controllerPos, periodId, eager, toMicros(queuedNanos),
                toMicros(totalNanos));
    }

    public static void logStructureFormationStarted(Level level, BlockPos controllerPos) {
        info(
                "[TRANSFINITE_ARRAY] event=structure_formed phase=start dimension={} game_time={} controller_pos={}",
                dimension(level), gameTime(level), controllerPos);
    }

    public static void logStructureFormed(Level level, BlockPos controllerPos, int partCount,
                                          @Nullable BlockPos interfacePos, boolean interfaceOnline,
                                          boolean nodeOnline, boolean nodePowered, boolean nodeActive,
                                          boolean gridPresent, long superclassNanos, long interfaceLookupNanos,
                                          long notificationNanos, long totalNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=structure_formed phase=complete result=success dimension={} " +
                        "game_time={} controller_pos={} part_count={} interface_pos={} interface_online={} node_online={} " +
                        "node_powered={} node_active={} grid_present={} superclass_us={} interface_lookup_us={} " +
                        "notification_us={} total_us={}",
                dimension(level), gameTime(level), controllerPos, partCount, interfacePos, interfaceOnline, nodeOnline,
                nodePowered, nodeActive, gridPresent, toMicros(superclassNanos), toMicros(interfaceLookupNanos),
                toMicros(notificationNanos), toMicros(totalNanos));
    }

    public static void logStructureFormationFailure(Level level, BlockPos controllerPos, long totalNanos,
                                                    Throwable exception) {
        error(
                "[TRANSFINITE_ARRAY] event=structure_formed phase=complete result=failed dimension={} " +
                        "game_time={} controller_pos={} total_us={}",
                exception, dimension(level), gameTime(level), controllerPos, toMicros(totalNanos));
    }

    public static void logStructureInvalidated(Level level, BlockPos controllerPos,
                                               @Nullable BlockPos previousInterfacePos,
                                               boolean previousInterfaceOnline, boolean previousNodeActive,
                                               boolean previousGridPresent, long totalNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=structure_invalidated dimension={} game_time={} controller_pos={} " +
                        "previous_interface_pos={} previous_interface_online={} previous_node_active={} " +
                        "previous_grid_present={} total_us={}",
                dimension(level), gameTime(level), controllerPos, previousInterfacePos, previousInterfaceOnline,
                previousNodeActive, previousGridPresent, toMicros(totalNanos));
    }

    public static void logNetworkCheckStarted(Level level, BlockPos interfacePos,
                                              List<BlockPos> controllerPositions, Object reason,
                                              boolean online, boolean nodeOnline, boolean nodePowered,
                                              boolean nodeActive, boolean gridPresent) {
        info(
                "[TRANSFINITE_ARRAY] event=network_check phase=start dimension={} game_time={} interface_pos={} " +
                        "controller_positions={} reason={} online={} node_online={} node_powered={} " +
                        "node_active={} grid_present={}",
                dimension(level), gameTime(level), interfacePos, controllerPositions, reason, online, nodeOnline, nodePowered,
                nodeActive, gridPresent);
    }

    public static void logNetworkCheck(Level level, BlockPos interfacePos, List<BlockPos> controllerPositions,
                                       Object reason, boolean onlineBefore, boolean onlineAfter,
                                       boolean nodeOnline, boolean nodePowered, boolean nodeActive,
                                       boolean gridPresent, long superclassNanos, long notificationNanos,
                                       long totalNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=network_check phase=complete dimension={} game_time={} interface_pos={} " +
                        "controller_positions={} reason={} online_before={} online_after={} node_online={} " +
                        "node_powered={} node_active={} grid_present={} superclass_us={} notification_us={} " +
                        "total_us={}",
                dimension(level), gameTime(level), interfacePos, controllerPositions, reason, onlineBefore, onlineAfter,
                nodeOnline, nodePowered, nodeActive, gridPresent, toMicros(superclassNanos),
                toMicros(notificationNanos), toMicros(totalNanos));
    }

    public static void logGridNodeHolderLoadStarted(Level level, BlockPos interfacePos,
                                                    boolean nodeOnline, boolean nodePowered,
                                                    boolean nodeActive, boolean gridPresent) {
        info(
                "[TRANSFINITE_ARRAY] event=grid_node_holder_load phase=start side={} dimension={} game_time={} " +
                        "interface_pos={} node_online={} node_powered={} node_active={} grid_present={}",
                side(level), dimension(level), gameTime(level), interfacePos, nodeOnline, nodePowered, nodeActive,
                gridPresent);
    }

    public static void logGridNodeHolderLoadCompleted(Level level, BlockPos interfacePos,
                                                      boolean nodeCreationQueued, boolean nodeOnline,
                                                      boolean nodePowered, boolean nodeActive,
                                                      boolean gridPresent, long totalNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=grid_node_holder_load phase=complete side={} dimension={} game_time={} " +
                        "interface_pos={} node_creation_queued={} node_online={} node_powered={} node_active={} " +
                        "grid_present={} total_us={}",
                side(level), dimension(level), gameTime(level), interfacePos, nodeCreationQueued, nodeOnline, nodePowered,
                nodeActive, gridPresent, toMicros(totalNanos));
    }

    public static void logGridNodeHolderUnloadStarted(Level level, BlockPos interfacePos,
                                                      boolean nodeOnline, boolean nodePowered,
                                                      boolean nodeActive, boolean gridPresent) {
        info(
                "[TRANSFINITE_ARRAY] event=grid_node_holder_unload phase=start side={} dimension={} game_time={} " +
                        "interface_pos={} node_online={} node_powered={} node_active={} grid_present={}",
                side(level), dimension(level), gameTime(level), interfacePos, nodeOnline, nodePowered, nodeActive,
                gridPresent);
    }

    public static void logGridNodeHolderUnloadCompleted(Level level, BlockPos interfacePos,
                                                        boolean nodeOnline, boolean nodePowered,
                                                        boolean nodeActive, boolean gridPresent,
                                                        long totalNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=grid_node_holder_unload phase=complete side={} dimension={} game_time={} " +
                        "interface_pos={} node_online={} node_powered={} node_active={} grid_present={} total_us={}",
                side(level), dimension(level), gameTime(level), interfacePos, nodeOnline, nodePowered, nodeActive, gridPresent,
                toMicros(totalNanos));
    }

    public static void logNodeCreationQueued(Level level, BlockPos interfacePos,
                                             boolean serverRunning, boolean serverStopped) {
        info(
                "[TRANSFINITE_ARRAY] event=node_creation phase=queued side={} dimension={} game_time={} interface_pos={} " +
                        "server_running={} server_stopped={}",
                side(level), dimension(level), gameTime(level), interfacePos, serverRunning, serverStopped);
    }

    public static void logNodeCreationStarted(Level level, BlockPos interfacePos, long queuedNanos,
                                              boolean nodeOnline, boolean nodePowered,
                                              boolean nodeActive, boolean gridPresent) {
        info(
                "[TRANSFINITE_ARRAY] event=node_creation phase=start side={} dimension={} game_time={} interface_pos={} " +
                        "queued_us={} node_online={} node_powered={} node_active={} grid_present={}",
                side(level), dimension(level), gameTime(level), interfacePos, toMicros(queuedNanos), nodeOnline, nodePowered,
                nodeActive, gridPresent);
    }

    public static void logNodeCreationCompleted(Level level, BlockPos interfacePos, long queuedNanos,
                                                long creationNanos, long totalNanos, boolean nodeOnline,
                                                boolean nodePowered, boolean nodeActive,
                                                boolean gridPresent) {
        info(
                "[TRANSFINITE_ARRAY] event=node_creation phase=complete result=success side={} dimension={} " +
                        "game_time={} interface_pos={} queued_us={} creation_us={} total_us={} node_online={} node_powered={} " +
                        "node_active={} grid_present={}",
                side(level), dimension(level), gameTime(level), interfacePos, toMicros(queuedNanos), toMicros(creationNanos),
                toMicros(totalNanos), nodeOnline, nodePowered, nodeActive, gridPresent);
    }

    public static void logNodeCreationCanceled(Level level, BlockPos interfacePos, String reason,
                                               long queuedNanos, long totalNanos) {
        info(
                "[TRANSFINITE_ARRAY] event=node_creation phase=complete result=canceled reason={} side={} " +
                        "dimension={} game_time={} interface_pos={} queued_us={} total_us={}",
                reason, side(level), dimension(level), gameTime(level), interfacePos, toMicros(queuedNanos),
                toMicros(totalNanos));
    }

    private static void info(String message, Object... arguments) {
        if (!isEnabled()) {
            return;
        }
        try {
            Logger dedicatedLogger = getLogger();
            if (dedicatedLogger != null) {
                dedicatedLogger.info(message, arguments);
                return;
            }
        } catch (Throwable exception) {
            disableDedicatedLogger(exception);
        }
        try {
            GTLCore.LOGGER.info(message, arguments);
        } catch (Throwable ignored) {
            // Lifecycle diagnostics must never interrupt machine operation.
        }
    }

    private static void error(String message, Throwable exception, Object... arguments) {
        if (!isEnabled()) {
            return;
        }
        Object[] argumentsWithException = Arrays.copyOf(arguments, arguments.length + 1);
        argumentsWithException[arguments.length] = exception;
        try {
            Logger dedicatedLogger = getLogger();
            if (dedicatedLogger != null) {
                dedicatedLogger.error(message, argumentsWithException);
                return;
            }
        } catch (Throwable loggingException) {
            disableDedicatedLogger(loggingException);
        }
        try {
            GTLCore.LOGGER.error(message, argumentsWithException);
        } catch (Throwable ignored) {
            // Lifecycle diagnostics must never interrupt machine operation.
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
                throw new IllegalStateException("Log4j2 did not create the transfinite array lifecycle appender");
            }

            appender.start();
            configuration.addAppender(appender);
            configuration.removeLogger(LOGGER_NAME);
            LoggerConfig loggerConfig = new LoggerConfig(LOGGER_NAME, org.apache.logging.log4j.Level.INFO, false);
            loggerConfig.addAppender(appender, org.apache.logging.log4j.Level.INFO, null);
            configuration.addLogger(LOGGER_NAME, loggerConfig);
            context.updateLoggers();

            Logger dedicatedLogger = LogManager.getLogger(LOGGER_NAME);
            activeAppenderName = appenderName;
            dedicatedLogger.info(
                    "[TRANSFINITE_ARRAY] event=logger_initialized log_file={} rollover_size={} rollover_files={}",
                    logFile.toAbsolutePath(), ROLLOVER_SIZE, ROLLOVER_FILE_COUNT);
            return dedicatedLogger;
        } catch (Throwable exception) {
            if (configuration != null) {
                configuration.removeLogger(LOGGER_NAME);
            }
            if (appender != null) {
                appender.stop();
            }
            activeAppenderName = null;
            initializationFailed = true;
            GTLCore.LOGGER.error("Failed to initialize the transfinite computation array lifecycle log", exception);
            return null;
        }
    }

    private static void disableDedicatedLogger(Throwable exception) {
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
            } catch (Throwable cleanupException) {
                exception.addSuppressed(cleanupException);
            } finally {
                logger = null;
                activeAppenderName = null;
                initializationFailed = true;
            }
        }
        try {
            GTLCore.LOGGER.error(
                    "Transfinite computation array lifecycle log failed; falling back to the main log", exception);
        } catch (Throwable ignored) {
            // There is no remaining logging backend to report this failure through.
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

    private static Object toMicros(long nanos) {
        return nanos == UNAVAILABLE_DURATION_NANOS ? UNKNOWN_DURATION : nanos / NANOS_PER_MICROSECOND;
    }

    private static Object dimension(@Nullable Level level) {
        return level == null ? UNKNOWN_DIMENSION : level.dimension().location();
    }

    private static Object gameTime(@Nullable Level level) {
        return level == null ? UNKNOWN_GAME_TIME : level.getGameTime();
    }

    private static String side(@Nullable Level level) {
        if (level == null) {
            return UNKNOWN_SIDE;
        }
        return level.isClientSide() ? "client" : "server";
    }
}
