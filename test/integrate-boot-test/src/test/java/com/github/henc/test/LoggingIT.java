package com.github.henc.test;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.slf4j.Log4jLoggerFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the logging layer aggregated by the starter: application code logs
 * through the SLF4J facade and the backend must be Log4j2, configured by the default
 * {@code log4j2.xml} shipped inside the {@code integrate-boot-logging} jar.
 */
@SpringBootTest
class LoggingIT {

    @Test
    void slf4jBindsToLog4j2() {
        assertThat(LoggerFactory.getILoggerFactory()).isInstanceOf(Log4jLoggerFactory.class);
    }

    @Test
    void logbackIsAbsentFromTheClasspath() {
        assertThat(ClassUtils.isPresent("ch.qos.logback.classic.LoggerContext",
                getClass().getClassLoader())).isFalse();
    }

    @Test
    void shippedDefaultConfigurationIsActive() {
        // If this fails, Log4j2 fell back to its built-in default (console, ERROR only) —
        // i.e. the module's log4j2.xml was not picked up from the classpath.
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        assertThat(context.getConfiguration().getName())
                .isNotEqualTo(DefaultConfiguration.DEFAULT_NAME);
    }

    @Test
    void defaultLevelIsInfoAndThirdPartyIsCappedAtWarn() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        // info tier: the root logger defaults to info (the debug block stays commented out).
        assertThat(context.getRootLogger().getLevel()).isEqualTo(Level.INFO);
        // warn tier: third-party components are capped at warn.
        assertThat(context.getConfiguration().getLoggerConfig("org.apache").getLevel())
                .isEqualTo(Level.WARN);
    }
}
