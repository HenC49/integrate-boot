package com.github.henc.integrateboot.jackson.config;

import com.github.henc.integrateboot.jackson.JacksonConst;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the integrate-boot Jackson layer.
 *
 * <p>Both fields carry Java-level defaults ({@link JacksonConst#DEFAULT_DATE_FORMAT} and
 * {@link JacksonConst#DEFAULT_TIME_ZONE}), so the formatting conventions apply <em>even when
 * nothing is configured under {@code integrate-boot.jackson.*}</em> — the application gets
 * {@code yyyy-MM-dd HH:mm:ss} in {@code GMT+8} with zero YAML.
 *
 * <p>Example (everything below is optional):
 * <pre>{@code
 * integrate-boot:
 *   jackson:
 *     date-format: yyyy-MM-dd HH:mm:ss
 *     time-zone: GMT+8
 * }</pre>
 */
@ConfigurationProperties(prefix = "integrate-boot.jackson")
public class JacksonProperties {

    /**
     * Pattern applied to {@code java.util.Date} / {@code java.util.Calendar} (via
     * {@code defaultDateFormat}) and to {@code java.time.LocalDateTime} (via a registered
     * {@link tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer}).
     */
    private String dateFormat = JacksonConst.DEFAULT_DATE_FORMAT;

    /**
     * Time zone applied to {@code java.util.Date} serialization and to the date format.
     * Defaults to {@link JacksonConst#DEFAULT_TIME_ZONE} because Jackson 3 otherwise uses UTC.
     */
    private String timeZone = JacksonConst.DEFAULT_TIME_ZONE;

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
