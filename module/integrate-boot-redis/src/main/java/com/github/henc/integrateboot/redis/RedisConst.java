package com.github.henc.integrateboot.redis;

/**
 * Shared constants for the integrate-boot Redis integration layer.
 *
 * <p>The default {@code redisTemplate} / {@code stringRedisTemplate} bean names match the ones
 * the Redisson Spring Boot starter (and Spring Boot itself) register, so the Jackson-serialized
 * templates defined by this module transparently replace the defaults. Extra instances declared
 * under {@code integrate-boot.redis.multi.<name>.*} are registered under names suffixed with the
 * instance key.
 */
public final class RedisConst {

    private RedisConst() {
    }

    /**
     * Default {@link org.springframework.data.redis.core.RedisTemplate} bean name. Matches the
     * name used by Spring Boot / Redisson so this module's bean wins via {@code @Primary} +
     * {@code @ConditionalOnMissingBean}.
     */
    public static final String REDIS_TEMPLATE = "redisTemplate";

    /**
     * Default {@link org.springframework.data.redis.core.StringRedisTemplate} bean name.
     */
    public static final String STRING_REDIS_TEMPLATE = "stringRedisTemplate";

    /**
     * Bean-name prefix for extra {@link org.redisson.api.RedissonClient} instances, e.g.
     * {@code redissonClient-user}.
     */
    public static final String REDISSON_CLIENT_PREFIX = "redissonClient-";

    /**
     * Bean-name prefix for extra {@link org.springframework.data.redis.core.RedisTemplate}
     * instances, e.g. {@code redisTemplate-user}.
     */
    public static final String REDIS_TEMPLATE_PREFIX = "redisTemplate-";

    /**
     * Bean-name prefix for extra {@link org.springframework.data.redis.core.StringRedisTemplate}
     * instances, e.g. {@code stringRedisTemplate-user}.
     */
    public static final String STRING_REDIS_TEMPLATE_PREFIX = "stringRedisTemplate-";
}
