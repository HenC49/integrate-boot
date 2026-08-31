package com.github.henc.integrateboot.logging;

/**
 * Shared constants for the integrate-boot logging layer.
 */
public final class LoggingConst {

    private LoggingConst() {
    }

    /**
     * The {@link org.slf4j.ILoggerFactory} implementation Log4j2's SLF4J binding installs
     * ({@code log4j-slf4j2-impl}). The startup check in
     * {@code LoggingAutoConfiguration} compares the bound factory against this name to
     * detect a classpath where another SLF4J provider won the race.
     */
    public static final String LOG4J2_LOGGER_FACTORY = "org.apache.logging.slf4j.Log4jLoggerFactory";

    /**
     * Logback's core class, used as a class-presence probe. When Spring Boot's default
     * {@code spring-boot-starter-logging} leaks onto the classpath next to this module,
     * SLF4J may bind to Logback and the shipped {@code log4j2.xml} is silently ignored.
     */
    public static final String LOGBACK_LOGGER_CONTEXT = "ch.qos.logback.classic.LoggerContext";
}
