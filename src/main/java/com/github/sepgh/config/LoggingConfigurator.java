package com.github.sepgh.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Programmatic logging configurator that optionally adds file logging.
 * <p>
 * By default only console logging is active (via {@code logback.xml}).
 * When {@code log_file_enabled: true} is set in config, this class adds a
 * {@link RollingFileAppender} with time-based rotation.
 * <p>
 * Configuration options in {@code config.yaml}:
 * <pre>
 * log_file_enabled: true                                       # Enable file logging (default: false)
 * log_file_path: "/var/log/proxy-balancer/proxy-balancer.log"  # Log file path
 * log_file_rotation_hours: 24                                  # Rotation interval in hours (default: 24)
 * </pre>
 * <p>
 * Rotated files are named with a date-hour suffix, e.g.
 * {@code proxy-balancer.2025-01-15-00.log}, and are kept for 30 periods.
 */
public class LoggingConfigurator {
    private static final Logger logger = LoggerFactory.getLogger(LoggingConfigurator.class);

    /**
     * Configures file logging based on the application config.
     * Must be called early in application startup, after config is loaded.
     *
     * @param config the application configuration
     */
    public static void configure(ApplicationConfig config) {
        if (!config.isLogFileEnabled()) {
            logger.debug("File logging is disabled");
            return;
        }

        String logFilePath = config.getLogFilePath();
        int rotationHours = config.getLogFileRotationHours();

        // Ensure parent directory exists
        File logFile = new File(logFilePath);
        File parentDir = logFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                logger.info("Created log directory: {}", parentDir.getAbsolutePath());
            } else {
                logger.error("Failed to create log directory: {}", parentDir.getAbsolutePath());
                return;
            }
        }

        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            encoder.start();

            RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
            fileAppender.setContext(context);
            fileAppender.setName("FILE");
            fileAppender.setFile(logFilePath);

            TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
            rollingPolicy.setContext(context);
            rollingPolicy.setParent(fileAppender);
            rollingPolicy.setMaxHistory(30);

            // Configure rotation pattern based on hours
            if (rotationHours <= 1) {
                rollingPolicy.setFileNamePattern(logFilePath + ".%d{yyyy-MM-dd-HH}.gz");
            } else if (rotationHours < 24) {
                // For sub-daily rotation, use hourly pattern
                rollingPolicy.setFileNamePattern(logFilePath + ".%d{yyyy-MM-dd-HH}.gz");
            } else {
                // Daily or longer rotation
                rollingPolicy.setFileNamePattern(logFilePath + ".%d{yyyy-MM-dd}.gz");
            }
            rollingPolicy.start();

            fileAppender.setRollingPolicy(rollingPolicy);
            fileAppender.setEncoder(encoder);
            fileAppender.start();

            ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(fileAppender);

            logger.info("File logging enabled: path={}, rotation={}h", logFilePath, rotationHours);
        } catch (Exception e) {
            logger.error("Failed to configure file logging", e);
        }
    }
}
