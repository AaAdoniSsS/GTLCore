package org.gtlcore.gtlcore.integration.world;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.ConfigHolder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Captures server-side world loading timings without adding work to the loading path. */
public final class WorldLoadPerformanceLogger {

    private static final String LOGGER_NAME = "gtlcore.world_load.performance";
    private static final String APPENDER_NAME_PREFIX = "GTLCoreWorldLoadPerformance-";
    private static final String LOG_FILE_PREFIX = "world-load-performance-";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final String ROLLOVER_FILE_SUFFIX = "-%i.log.gz";
    private static final String LOG_DIRECTORY = "logs/gtlcore";
    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd_HH-mm-ss-SSS";
    private static final String LOG_LAYOUT = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %msg%n";
    private static final String ROLLOVER_SIZE = "16 MB";
    private static final String ROLLOVER_FILE_COUNT = "4";
    private static final long SLOW_WORLD_LOAD_MILLIS = 5_000L;
    private static final int MANY_LEVELS = 4;
    private static final int MANY_FORCED_CHUNKS = 100;
    private static final int HIGH_HEAP_USAGE_PERCENT = 85;
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;

    private static final Object LOCK = new Object();
    private static volatile Logger logger;
    private static volatile String activeAppenderName;
    private static volatile boolean initializationFailed;
    private static volatile Session activeSession;

    private WorldLoadPerformanceLogger() {}

    public static boolean isEnabled() {
        return ConfigHolder.INSTANCE != null && ConfigHolder.INSTANCE.enableWorldLoadPerformanceLogging;
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!isEnabled()) return;
        synchronized (LOCK) {
            Session session = getOrCreateSession(event.getServer(), "server_about_to_start");
            info("[WORLD_LOAD] event=server_about_to_start server={} session_start_ms=0", session.serverName());
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        if (!isEnabled()) return;
        synchronized (LOCK) {
            Session session = getOrCreateSession(event.getServer(), "server_started");
            session.serverStartedNanos = System.nanoTime();
            writeSummary(session, "complete");
        }
    }

    public static void onServerStarting(ServerStartingEvent event) {
        if (!isEnabled()) return;
        synchronized (LOCK) {
            Session session = getOrCreateSession(event.getServer(), "server_starting");
            session.serverStartingNanos = System.nanoTime();
            info("[WORLD_LOAD] event=server_starting world_preparation_ms={}",
                    toMillis(session.serverStartingNanos - session.startedNanos));
        }
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (!isEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        synchronized (LOCK) {
            Session session = getOrCreateSession(level.getServer(), "level_load");
            session.recordLevel(level);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!isEnabled()) return;
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        synchronized (LOCK) {
            Session session = getOrCreateSession(level.getServer(), "player_logged_in");
            info("[WORLD_LOAD] event=player_enter_world_completed player={} dimension={} " +
                    "elapsed_since_session_ms={} " +
                    "elapsed_since_server_started_ms={}",
                    player.getGameProfile().getName(), dimension(level),
                    toMillis(System.nanoTime() - session.startedNanos),
                    session.serverStartedNanos == 0 ? -1 : toMillis(System.nanoTime() - session.serverStartedNanos));
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        synchronized (LOCK) {
            Session session = activeSession;
            if (session == null || session.server != event.getServer()) return;
            if (!isEnabled()) {
                activeSession = null;
                return;
            }
            if (!session.summaryWritten) writeSummary(session, "stopping_before_started");
            info("[WORLD_LOAD] event=server_stopping server={} session_elapsed_ms={}", session.serverName(),
                    toMillis(System.nanoTime() - session.startedNanos));
            activeSession = null;
        }
    }

    private static Session getOrCreateSession(MinecraftServer server, String trigger) {
        Session session = activeSession;
        if (session == null || session.server != server) {
            session = new Session(server, System.nanoTime());
            activeSession = session;
            info("[WORLD_LOAD] event=session_started trigger={} server={} " +
                    "machine_startup_tick_budget_enabled={} normal_budget={} ae_budget={} time_budget_ms={}",
                    trigger, session.serverName(), machineStartupBudgetEnabled(), normalBudget(), aeBudget(),
                    timeBudgetMillis());
        }
        return session;
    }

    private static void writeSummary(Session session, String status) {
        if (session.summaryWritten) return;
        long elapsedMillis = toMillis((session.serverStartedNanos == 0 ? System.nanoTime() : session.serverStartedNanos) -
                session.startedNanos);
        Runtime runtime = Runtime.getRuntime();
        long usedHeapBytes = runtime.totalMemory() - runtime.freeMemory();
        long maxHeapBytes = runtime.maxMemory();
        int heapUsagePercent = maxHeapBytes <= 0 ? 0 : (int) Math.min(100L, usedHeapBytes * 100L / maxHeapBytes);
        int loadedChunks = session.levels.values().stream().mapToInt(LevelObservation::loadedChunks)
                .filter(count -> count >= 0).sum();
        int forcedChunks = session.levels.values().stream().mapToInt(LevelObservation::forcedChunks)
                .filter(count -> count >= 0).sum();
        long worldPreparationMillis = session.serverStartingNanos == 0 ? -1 :
                toMillis(session.serverStartingNanos - session.startedNanos);
        long startupCompletionMillis = session.serverStartingNanos == 0 || session.serverStartedNanos == 0 ? -1 :
                toMillis(session.serverStartedNanos - session.serverStartingNanos);
        session.summaryWritten = true;
        info("[WORLD_LOAD] event=summary status={} server={} total_ms={} world_preparation_ms={} " +
                "startup_completion_ms={} levels={} level_load_events={} " +
                "loaded_chunks={} forced_chunks={} used_heap_mib={} max_heap_mib={} heap_usage_percent={} " +
                "available_processors={} optimization_candidates={}",
                status, session.serverName(), elapsedMillis, worldPreparationMillis, startupCompletionMillis,
                session.levels.size(), session.levelLoadEvents,
                loadedChunks, forcedChunks, usedHeapBytes / BYTES_PER_MEBIBYTE, maxHeapBytes / BYTES_PER_MEBIBYTE,
                heapUsagePercent, runtime.availableProcessors(),
                countCandidates(session, elapsedMillis, forcedChunks, heapUsagePercent));
        for (LevelObservation observation : session.levels.values()) {
            info("[WORLD_LOAD] event=level_load_observed dimension={} first_seen_ms={} last_seen_ms={} " +
                    "occurrences={} loaded_chunks={} forced_chunks={}",
                    observation.dimension, toMillis(observation.firstSeenNanos - session.startedNanos),
                    toMillis(observation.lastSeenNanos - session.startedNanos), observation.occurrences,
                    observation.loadedChunks(), observation.forcedChunks());
        }
        if (elapsedMillis >= SLOW_WORLD_LOAD_MILLIS) {
            info("[WORLD_LOAD] event=optimization_candidate candidate=slow_server_world_load total_ms={} " +
                    "action=profile_chunk_loading_nbt_and_machine_startup_ticks",
                    elapsedMillis);
        }
        if (session.levels.size() >= MANY_LEVELS) {
            info("[WORLD_LOAD] event=optimization_candidate candidate=many_dimensions level_count={} " +
                    "action=profile_dimension_initialization_and_forced_chunks",
                    session.levels.size());
        }
        if (!machineStartupBudgetEnabled()) {
            info("[WORLD_LOAD] event=optimization_candidate candidate=machine_startup_budget_disabled " +
                    "action=enableMachineStartupTickBudget");
        }
        if (forcedChunks >= MANY_FORCED_CHUNKS) {
            info("[WORLD_LOAD] event=optimization_candidate candidate=many_forced_chunks forced_chunks={} " +
                    "action=reduce_or_stagger_forced_chunk_tickets",
                    forcedChunks);
        }
        if (heapUsagePercent >= HIGH_HEAP_USAGE_PERCENT) {
            info("[WORLD_LOAD] event=optimization_candidate candidate=high_heap_usage heap_usage_percent={} " +
                    "action=profile_retained_world_data_and_review_heap_capacity",
                    heapUsagePercent);
        }
    }

    private static int countCandidates(Session session, long elapsedMillis, int forcedChunks, int heapUsagePercent) {
        int candidates = 0;
        if (elapsedMillis >= SLOW_WORLD_LOAD_MILLIS) candidates++;
        if (session.levels.size() >= MANY_LEVELS) candidates++;
        if (!machineStartupBudgetEnabled()) candidates++;
        if (forcedChunks >= MANY_FORCED_CHUNKS) candidates++;
        if (heapUsagePercent >= HIGH_HEAP_USAGE_PERCENT) candidates++;
        return candidates;
    }

    private static boolean machineStartupBudgetEnabled() {
        return ConfigHolder.INSTANCE == null || ConfigHolder.INSTANCE.enableMachineStartupTickBudget;
    }

    private static int normalBudget() {
        return ConfigHolder.INSTANCE == null ? ConfigHolder.DEFAULT_MACHINE_STARTUP_TICK_BUDGET_PER_LEVEL :
                ConfigHolder.INSTANCE.machineStartupTickBudgetPerLevel;
    }

    private static int aeBudget() {
        return ConfigHolder.INSTANCE == null ? ConfigHolder.DEFAULT_MACHINE_STARTUP_AE_TICK_BUDGET_PER_LEVEL :
                ConfigHolder.INSTANCE.machineStartupAeTickBudgetPerLevel;
    }

    private static int timeBudgetMillis() {
        return ConfigHolder.INSTANCE == null ? ConfigHolder.DEFAULT_MACHINE_STARTUP_TICK_TIME_BUDGET_MILLIS :
                ConfigHolder.INSTANCE.machineStartupTickTimeBudgetMillis;
    }

    private static void info(String message, Object... arguments) {
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
            // Diagnostics must never interrupt server startup.
        }
    }

    @Nullable
    private static Logger getLogger() {
        if (!isEnabled() || initializationFailed) return null;
        Logger currentLogger = logger;
        if (currentLogger != null && isDedicatedLoggerConfigured()) return currentLogger;
        synchronized (LOCK) {
            if (logger == null || !isDedicatedLoggerConfigured()) {
                logger = createLogger();
            }
            return logger;
        }
    }

    @Nullable
    private static Logger createLogger() {
        Configuration configuration = null;
        RollingFileAppender appender = null;
        try {
            String timestamp = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN, Locale.ROOT)
                    .format(LocalDateTime.now());
            Path directory = FMLPaths.GAMEDIR.get().resolve(LOG_DIRECTORY);
            Files.createDirectories(directory);
            Path logFile = directory.resolve(LOG_FILE_PREFIX + timestamp + LOG_FILE_EXTENSION);
            String filePattern = directory.resolve(LOG_FILE_PREFIX + timestamp + ROLLOVER_FILE_SUFFIX).toString();
            if (!(LogManager.getContext(false) instanceof LoggerContext context)) {
                throw new IllegalStateException("Log4j2 core LoggerContext is unavailable");
            }
            configuration = context.getConfiguration();
            String appenderName = APPENDER_NAME_PREFIX + timestamp;
            PatternLayout layout = PatternLayout.newBuilder().withConfiguration(configuration)
                    .withCharset(StandardCharsets.UTF_8).withPattern(LOG_LAYOUT).build();
            appender = RollingFileAppender.newBuilder().setConfiguration(configuration).setName(appenderName)
                    .setLayout(layout).setIgnoreExceptions(false).withFileName(logFile.toString())
                    .withFilePattern(filePattern).withAppend(true).withCreateOnDemand(false)
                    .withPolicy(SizeBasedTriggeringPolicy.createPolicy(ROLLOVER_SIZE))
                    .withStrategy(DefaultRolloverStrategy.newBuilder().withConfig(configuration)
                            .withMax(ROLLOVER_FILE_COUNT).build())
                    .build();
            if (appender == null) throw new IllegalStateException("World load appender was not created");
            appender.start();
            configuration.addAppender(appender);
            configuration.removeLogger(LOGGER_NAME);
            LoggerConfig loggerConfig = new LoggerConfig(LOGGER_NAME, org.apache.logging.log4j.Level.INFO, false);
            loggerConfig.addAppender(appender, org.apache.logging.log4j.Level.INFO, null);
            configuration.addLogger(LOGGER_NAME, loggerConfig);
            context.updateLoggers();
            Logger dedicatedLogger = LogManager.getLogger(LOGGER_NAME);
            activeAppenderName = appenderName;
            dedicatedLogger.info("[WORLD_LOAD] event=logger_initialized log_file={} rollover_size={} rollover_files={}",
                    logFile.toAbsolutePath(), ROLLOVER_SIZE, ROLLOVER_FILE_COUNT);
            return dedicatedLogger;
        } catch (Throwable exception) {
            if (configuration != null) configuration.removeLogger(LOGGER_NAME);
            if (appender != null) appender.stop();
            activeAppenderName = null;
            initializationFailed = true;
            GTLCore.LOGGER.error("Failed to initialize the world load performance log", exception);
            return null;
        }
    }

    private static void disableDedicatedLogger(Throwable exception) {
        initializationFailed = true;
        activeAppenderName = null;
        logger = null;
        GTLCore.LOGGER.error("World load performance log failed; falling back to the main log", exception);
    }

    private static boolean isDedicatedLoggerConfigured() {
        String appenderName = activeAppenderName;
        if (appenderName == null || !(LogManager.getContext(false) instanceof LoggerContext context)) return false;
        LoggerConfig loggerConfig = context.getConfiguration().getLoggers().get(LOGGER_NAME);
        return loggerConfig != null && !loggerConfig.isAdditive() && loggerConfig.getAppenders().containsKey(appenderName);
    }

    private static String dimension(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        return dimension.location().toString();
    }

    private static long toMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private static final class Session {

        private final MinecraftServer server;
        private final long startedNanos;
        private final Map<String, LevelObservation> levels = new LinkedHashMap<>();
        private long serverStartingNanos;
        private long serverStartedNanos;
        private int levelLoadEvents;
        private boolean summaryWritten;

        private Session(MinecraftServer server, long startedNanos) {
            this.server = server;
            this.startedNanos = startedNanos;
        }

        private void recordLevel(ServerLevel level) {
            long now = System.nanoTime();
            String dimension = dimension(level);
            levelLoadEvents++;
            levels.compute(dimension, (ignored, current) -> {
                if (current == null) return new LevelObservation(dimension, level, now);
                current.level = level;
                current.lastSeenNanos = now;
                current.occurrences++;
                return current;
            });
            info("[WORLD_LOAD] event=level_load dimension={} elapsed_since_session_ms={} game_time={}", dimension,
                    toMillis(now - startedNanos), level.getGameTime());
        }

        private String serverName() {
            return server.getServerModName();
        }
    }

    private static final class LevelObservation {

        private final String dimension;
        private final long firstSeenNanos;
        private ServerLevel level;
        private long lastSeenNanos;
        private int occurrences = 1;

        private LevelObservation(String dimension, ServerLevel level, long firstSeenNanos) {
            this.dimension = dimension;
            this.level = level;
            this.firstSeenNanos = firstSeenNanos;
            this.lastSeenNanos = firstSeenNanos;
        }

        private int loadedChunks() {
            try {
                return level.getChunkSource().getLoadedChunksCount();
            } catch (Throwable exception) {
                GTLCore.LOGGER.warn("Failed to read loaded chunk count for {}", dimension, exception);
                return -1;
            }
        }

        private int forcedChunks() {
            try {
                return level.getForcedChunks().size();
            } catch (Throwable exception) {
                GTLCore.LOGGER.warn("Failed to read forced chunk count for {}", dimension, exception);
                return -1;
            }
        }
    }
}
