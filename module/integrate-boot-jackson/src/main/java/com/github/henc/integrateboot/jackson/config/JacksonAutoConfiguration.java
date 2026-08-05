package com.github.henc.integrateboot.jackson.config;

import com.github.henc.integrateboot.jackson.JacksonConst;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.module.SimpleModule;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson auto-configuration for integrate-boot.
 *
 * <p>Contributes two things:
 * <ol>
 *   <li><b>Web-global date/time format.</b> Registers a {@link JsonMapperBuilderCustomizer} so
 *   Spring Boot's auto-configured {@link ObjectMapper} serializes {@code java.util.Date} and
 *   {@code java.time.LocalDateTime} as {@code yyyy-MM-dd HH:mm:ss} (in {@code GMT+8}) by default.
 *   This needs no YAML — the values come from {@link JacksonProperties}' field defaults.</li>
 *   <li><b>Typed mapper for caches / RPC.</b> Exposes a standalone {@code typedObjectMapper}
 *   bean that also activates default typing (writes {@code @class}), so Redis values and
 *   micro-service payloads round-trip into their original concrete types.</li>
 * </ol>
 *
 * <p>The typed mapper is deliberately <em>not</em> {@code @Primary} — web responses keep using
 * the clean global mapper without type noise.
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@EnableConfigurationProperties(JacksonProperties.class)
public class JacksonAutoConfiguration {

    /**
     * Customize Spring Boot's global {@link JsonMapper.Builder} so both {@code Date} and
     * {@code LocalDateTime} serialize with the configured pattern/timezone. Runs as part of the
     * standard customizer chain, so every {@link ObjectMapper} Boot builds picks it up.
     */
    @Bean
    public JsonMapperBuilderCustomizer integrateBootJsonMapperBuilderCustomizer(JacksonProperties properties) {
        return builder -> applyDateFormat(builder, properties);
    }

    /**
     * Standalone {@link ObjectMapper} that serializes with default typing ({@code @class}) plus
     * the same date/time formatting as the global mapper. Intended for Redis values and
     * micro-service payloads where the concrete type must survive serialization.
     */
    @Bean(JacksonConst.TYPED_OBJECT_MAPPER)
    @ConditionalOnMissingBean(name = JacksonConst.TYPED_OBJECT_MAPPER)
    public ObjectMapper typedObjectMapper(JacksonProperties properties) {
        // Allow any non-final object root to carry type information, equivalent to the legacy
        // laissez-faire validator but built from the public API (the built-in one is package-private).
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        JsonMapper.Builder builder = JsonMapper.builder()
                .findAndAddModules()
                .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL);
        applyDateFormat(builder, properties);
        return builder.build();
    }

    /**
     * Apply the configured date format and timezone to a mapper builder. Handles both date
     * families: {@code java.util.Date} via {@code defaultDateFormat}, and
     * {@code java.time.LocalDateTime} via a dedicated serializer (since {@code defaultDateFormat}
     * does not cover {@code java.time} types).
     */
    private static void applyDateFormat(MapperBuilder<?, ?> builder, JacksonProperties properties) {
        String pattern = properties.getDateFormat();
        if (pattern == null || pattern.isEmpty()) {
            return;
        }
        TimeZone timeZone = resolveTimeZone(properties.getTimeZone());

        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
        dateFormat.setTimeZone(timeZone);
        builder.defaultDateFormat(dateFormat);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern).withZone(timeZone.toZoneId());
        SimpleModule dateModule = new SimpleModule("integrate-boot-date-format")
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        builder.addModule(dateModule);
    }

    private static TimeZone resolveTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isEmpty()) {
            return TimeZone.getTimeZone(JacksonConst.DEFAULT_TIME_ZONE);
        }
        return TimeZone.getTimeZone(timeZone);
    }
}
