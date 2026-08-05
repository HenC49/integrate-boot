package com.github.henc.integrateboot.redis.config;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Internal helper that applies the module's standard serializers to a {@link RedisTemplate}.
 *
 * <p>Centralizes the Jackson configuration (default typing for polymorphic deserialization) so
 * the default template and every extra multi-instance template serialize identically. Uses
 * Spring Data Redis' Jackson 3.x serializer ({@link GenericJacksonJsonRedisSerializer}), which
 * matches the Jackson version shipped with Spring Boot 4.1.
 */
final class RedisSerializers {

    static final StringRedisSerializer STRING = StringRedisSerializer.UTF_8;

    /** Lazily built once; safe to share across templates as serializers are stateless readers. */
    private static final RedisSerializer<Object> JSON = buildJsonSerializer();

    private RedisSerializers() {
    }

    /**
     * Apply String keys + JSON values/hash-values to the given template.
     */
    static void apply(RedisTemplate<?, ?> template) {
        template.setKeySerializer(STRING);
        template.setHashKeySerializer(STRING);
        template.setValueSerializer(JSON);
        template.setHashValueSerializer(JSON);
    }

    /**
     * Build a {@link RedisTemplate} bound to {@code connectionFactory} with the standard
     * serializers, ready for use.
     */
    static RedisTemplate<Object, Object> buildTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        apply(template);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Build a {@link StringRedisTemplate} bound to {@code connectionFactory}, ready for use.
     */
    static StringRedisTemplate buildStringTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * JSON serializer that writes the concrete type as a {@code @class} property, allowing
     * values to be deserialized back into their original types. {@code findAndAddModules}
     * registers the JSR310 (java.time) and JDK8 support so cached date/time types round-trip.
     */
    private static RedisSerializer<Object> buildJsonSerializer() {
        // Allow polymorphic deserialization for any non-final object type so cached values
        // restore their original concrete classes (equivalent to the legacy laissez-faire
        // validator, built from the public API since the built-in one is package-private).
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL)
                .build();
        return new GenericJacksonJsonRedisSerializer(mapper);
    }
}
