package com.github.henc.integrateboot.redis.config;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.ObjectMapper;

/**
 * Internal helper that applies the module's standard serializers to a {@link RedisTemplate}.
 *
 * <p>Uses the shared {@code typedObjectMapper} (Jackson 3, with type information) from the
 * integrate-boot-jackson module, so cached values serialize the same way everywhere — including
 * the configured date/time format. Keys and hash-keys use a {@link StringRedisSerializer}.
 */
final class RedisSerializers {

    static final StringRedisSerializer STRING = StringRedisSerializer.UTF_8;

    private RedisSerializers() {
    }

    /**
     * Build a JSON value serializer backed by the given {@link ObjectMapper}.
     */
    static RedisSerializer<Object> jsonSerializer(ObjectMapper objectMapper) {
        return new GenericJacksonJsonRedisSerializer(objectMapper);
    }

    /**
     * Build a {@link RedisTemplate} bound to {@code connectionFactory} with String keys and
     * JSON values (using {@code objectMapper}), ready for use.
     */
    static RedisTemplate<Object, Object> buildTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        RedisSerializer<Object> json = jsonSerializer(objectMapper);
        template.setKeySerializer(STRING);
        template.setHashKeySerializer(STRING);
        template.setValueSerializer(json);
        template.setHashValueSerializer(json);
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
}
