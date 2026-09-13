package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.ConfigHolder;

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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class WirelessAePerformanceLogger {

    private static final String LOGGER_NAME = "gtlcore.wireless_ae.performance";
    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT);
    private static volatile Logger logger;
    private static long nextInitializationAttempt;

    private WirelessAePerformanceLogger() {}

    static void log(int queuedNetworks, int retryMembers, int connectedNetworks) {
        if (ConfigHolder.INSTANCE == null || !ConfigHolder.INSTANCE.debugLogging.enableWirelessAeNetworkPerformanceLogging) return;
        try {
            Logger target = logger();
            if (target != null) target.info("event=diagnostics retry_count_mode=incremental scan_mode=cursor scan_member_limit=128 scan_network_member_limit=32 scan_network_limit=32 scan_budget_micros=500 maintenance_budget_micros=2000 queued_networks={} retry_members={} connected_networks={} {}",
                    queuedNetworks, retryMembers, connectedNetworks, WirelessAeDiagnostics.summary());
        } catch (RuntimeException error) {
            WirelessAeDiagnostics.failure("performance_log", null, null, error);
        }
    }

    private static Logger logger() {
        if (logger != null) return logger;
        synchronized (LOCK) {
            if (logger != null) return logger;
            long now = System.nanoTime();
            if (nextInitializationAttempt != 0 && now - nextInitializationAttempt < 0) return null;
            nextInitializationAttempt = now + 60_000_000_000L;
            try {
                if (!(LogManager.getContext(false) instanceof LoggerContext context)) return null;
                Configuration configuration = context.getConfiguration();
                String timestamp = TIMESTAMP.format(LocalDateTime.now());
                Path directory = FMLPaths.GAMEDIR.get().resolve("logs").resolve("gtlcore");
                Files.createDirectories(directory);
                Path file = directory.resolve("wireless-ae-network-performance-" + timestamp + ".log");
                String name = "GTLCoreWirelessAePerformance-" + timestamp;
                RollingFileAppender appender = RollingFileAppender.newBuilder().setConfiguration(configuration).setName(name)
                        .setLayout(PatternLayout.newBuilder().withConfiguration(configuration).withCharset(StandardCharsets.UTF_8)
                                .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %msg%n").build())
                        .setIgnoreExceptions(false).withFileName(file.toString()).withFilePattern(file + "-%i.gz")
                        .withAppend(true).withCreateOnDemand(false).withPolicy(SizeBasedTriggeringPolicy.createPolicy("16 MB"))
                        .withStrategy(DefaultRolloverStrategy.newBuilder().withConfig(configuration).withMax("4").build()).build();
                appender.start();
                configuration.addAppender(appender);
                configuration.removeLogger(LOGGER_NAME);
                LoggerConfig config = new LoggerConfig(LOGGER_NAME, org.apache.logging.log4j.Level.INFO, false);
                config.addAppender(appender, org.apache.logging.log4j.Level.INFO, null);
                configuration.addLogger(LOGGER_NAME, config);
                context.updateLoggers();
                logger = LogManager.getLogger(LOGGER_NAME);
                logger.info("event=logger_initialized log_file={}", file.toAbsolutePath());
                return logger;
            } catch (Exception error) {
                GTLCore.LOGGER.error("Unable to initialize wireless AE performance log", error);
                return null;
            }
        }
    }
}
