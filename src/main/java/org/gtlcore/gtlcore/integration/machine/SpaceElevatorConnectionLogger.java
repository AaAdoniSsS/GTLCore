package org.gtlcore.gtlcore.integration.machine;

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
import java.util.Locale;

public final class SpaceElevatorConnectionLogger {

    private static final String LOGGER_NAME = "gtlcore.space_elevator.connection";
    private static final String APPENDER_NAME_PREFIX = "GTLCoreSpaceElevatorConnection-";
    private static final String LOG_FILE_PREFIX = "space-elevator-connection-";
    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd_HH-mm-ss-SSS";
    private static final String LOG_LAYOUT = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %msg%n";
    private static final String ROLLOVER_SIZE = "16 MB";
    private static final String ROLLOVER_FILE_COUNT = "4";

    private static final Object INITIALIZATION_LOCK = new Object();
    private static volatile Logger logger;
    private static volatile String activeAppenderName;
    private static volatile boolean initializationFailed;

    private SpaceElevatorConnectionLogger() {}

    public static boolean isEnabled() {
        return ConfigHolder.INSTANCE != null && ConfigHolder.INSTANCE.debugLogging.enableSpaceElevatorConnectionLogging;
    }

    public static void logScan(Level level, String owner, BlockPos ownerPos, String trigger,
                               @Nullable BlockPos powerCorePos, @Nullable BlockPos savedHostPos,
                               BlockPos[] candidates) {
        info("[SPACE_ELEVATOR] event=scan owner={} trigger={} dimension={} game_time={} owner_pos={} " +
                "power_core_pos={} saved_host_pos={} candidate_count={} candidates={}",
                owner, trigger, dimension(level), gameTime(level), ownerPos, powerCorePos, savedHostPos,
                candidates.length, Arrays.toString(candidates));
    }

    public static void logCandidate(Level level, String owner, BlockPos ownerPos, BlockPos candidatePos,
                                    String candidateType, boolean formed, String result) {
        info("[SPACE_ELEVATOR] event=candidate owner={} dimension={} game_time={} owner_pos={} " +
                "candidate_pos={} candidate_type={} candidate_formed={} result={}",
                owner, dimension(level), gameTime(level), ownerPos, candidatePos, candidateType, formed, result);
    }

    public static void logConnection(Level level, BlockPos modulePos, BlockPos hostPos, String trigger,
                                     boolean repairedHostRegistration) {
        info("[SPACE_ELEVATOR] event=connected trigger={} dimension={} game_time={} module_pos={} host_pos={} " +
                "repaired_host_registration={}",
                trigger, dimension(level), gameTime(level), modulePos, hostPos, repairedHostRegistration);
    }

    public static void logDisconnection(Level level, BlockPos modulePos, @Nullable BlockPos hostPos, String reason) {
        info("[SPACE_ELEVATOR] event=disconnected reason={} dimension={} game_time={} module_pos={} host_pos={}",
                reason, dimension(level), gameTime(level), modulePos, hostPos);
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
        GTLCore.LOGGER.info(message, arguments);
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
                logger = createLogger();
            }
            return logger;
        }
    }

    private static Logger createLogger() {
        Configuration configuration = null;
        RollingFileAppender appender = null;
        try {
            String timestamp = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN, Locale.ROOT).format(LocalDateTime.now());
            Path logDirectory = FMLPaths.GAMEDIR.get().resolve("logs").resolve("gtlcore");
            Files.createDirectories(logDirectory);
            Path logFile = logDirectory.resolve(LOG_FILE_PREFIX + timestamp + ".log");
            String filePattern = logDirectory.resolve(LOG_FILE_PREFIX + timestamp + "-%i.log.gz").toString();

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
                throw new IllegalStateException("Log4j2 did not create the space elevator rolling file appender");
            }

            appender.start();
            configuration.addAppender(appender);
            configuration.removeLogger(LOGGER_NAME);
            LoggerConfig loggerConfig = new LoggerConfig(LOGGER_NAME, org.apache.logging.log4j.Level.INFO, false);
            loggerConfig.addAppender(appender, org.apache.logging.log4j.Level.INFO, null);
            configuration.addLogger(LOGGER_NAME, loggerConfig);
            context.updateLoggers();

            activeAppenderName = appenderName;
            Logger dedicatedLogger = LogManager.getLogger(LOGGER_NAME);
            dedicatedLogger.info("[SPACE_ELEVATOR] event=logger_initialized log_file={} rollover_size={} rollover_files={}",
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
            GTLCore.LOGGER.warn("Unable to initialize the space elevator connection logger", exception);
            return null;
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
            } catch (Throwable ignored) {
                // Connection recovery must remain independent from diagnostic logging failures.
            } finally {
                logger = null;
                activeAppenderName = null;
                initializationFailed = true;
            }
        }
        GTLCore.LOGGER.warn("Disabling the space elevator connection logger after an error", exception);
    }

    private static String dimension(Level level) {
        return level == null ? "unknown" : level.dimension().location().toString();
    }

    private static String gameTime(Level level) {
        return level == null ? "unknown" : Long.toString(level.getGameTime());
    }
}
