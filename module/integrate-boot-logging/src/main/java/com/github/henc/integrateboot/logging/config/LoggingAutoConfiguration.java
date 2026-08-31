package com.github.henc.integrateboot.logging.config;

import com.github.henc.integrateboot.logging.LoggingConst;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

/**
 * Logging auto-configuration for integrate-boot.
 *
 * <p>The actual log output is <em>not</em> configured here: Log4j2 initializes from the
 * {@code log4j2.xml} shipped at this module's jar root long before the Spring context exists
 * (Spring Boot's {@code Log4j2LoggingSystem} picks it up by the standard classpath lookup),
 * and runtime level changes flow through Boot's regular {@code logging.level.*} properties.
 * There is therefore deliberately no {@code @ConfigurationProperties} class — a Spring-side
 * property could not influence the XML anyway.
 *
 * <p>What this configuration adds is a startup guard: once all singletons are ready it
 * verifies that the classpath really is in the intended shape — SLF4J bound to Log4j2,
 * Logback absent, and a real (non-built-in) Log4j2 configuration active — and logs a loud,
 * actionable {@code ERROR} otherwise, so a misassembled classpath fails visibly instead of
 * silently losing the platform logging defaults.
 */
@AutoConfiguration
@ConditionalOnClass(LoggerContext.class)
public class LoggingAutoConfiguration {

    /**
     * Runs after every singleton is instantiated (real boot and {@code @SpringBootTest}
     * alike) and checks the logging backend assembled on the classpath against the intended
     * SLF4J-to-Log4j2 wiring.
     */
    @Bean
    public SmartInitializingSingleton integrateBootLoggingVerifier() {
        return LoggingAutoConfiguration::verifyLoggingBackend;
    }

    /**
     * Guard logic: each failure mode reports itself with the fix, then bails out so the
     * follow-up checks cannot report misleading details about a broken setup.
     */
    private static void verifyLoggingBackend() {
        Logger logger = LoggerFactory.getLogger(LoggingAutoConfiguration.class);

        if (ClassUtils.isPresent(LoggingConst.LOGBACK_LOGGER_CONTEXT,
                LoggingAutoConfiguration.class.getClassLoader())) {
            logger.error("Logback (spring-boot-starter-logging) is on the classpath together with "
                    + "integrate-boot-logging (Log4j2). Which backend SLF4J binds to becomes a "
                    + "classpath-order accident and the shipped log4j2.xml may be ignored. Exclude "
                    + "'org.springframework.boot:spring-boot-starter-logging' from every Spring Boot "
                    + "starter you declare.");
            return;
        }

        String boundFactory = LoggerFactory.getILoggerFactory().getClass().getName();
        if (!LoggingConst.LOG4J2_LOGGER_FACTORY.equals(boundFactory)) {
            logger.error("SLF4J is bound to '{}' but integrate-boot-logging expects '{}' (Log4j2). "
                            + "Inspect the classpath for other SLF4J providers.",
                    boundFactory, LoggingConst.LOG4J2_LOGGER_FACTORY);
            return;
        }

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        if (DefaultConfiguration.DEFAULT_NAME.equals(configuration.getName())) {
            logger.warn("Log4j2 is running on its built-in default configuration (console, ERROR "
                    + "only) — no log4j2.xml / log4j2-spring.xml was found on the classpath. "
                    + "integrate-boot-logging ships a default 'log4j2.xml'; make sure it was not "
                    + "excluded or stripped, or provide your own configuration.");
            return;
        }

        logger.info("integrate-boot logging initialized: SLF4J -> Log4j2, configuration: {}",
                configuration.getConfigurationSource().getLocation());
    }
}
