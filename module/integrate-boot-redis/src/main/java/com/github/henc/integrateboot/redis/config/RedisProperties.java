package com.github.henc.integrateboot.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for the integrate-boot Redis layer.
 *
 * <p>The default Redis connection is configured through Spring Boot's native
 * {@code spring.data.redis.*} properties and auto-configured by the Redisson Spring Boot
 * starter. This class only governs the <em>extra</em> instances declared under
 * {@code integrate-boot.redis.multi.*}:
 *
 * <pre>{@code
 * integrate-boot:
 *   redis:
 *     multi:
 *       user:
 *         host: 10.0.0.1
 *         port: 6379
 *         password: pass-user
 *         database: 1
 * }</pre>
 *
 * <p>Each entry yields three named beans:
 * <ul>
 *   <li>{@code redissonClient-<name>} ({@link org.redisson.api.RedissonClient})</li>
 *   <li>{@code redisTemplate-<name>} ({@link org.springframework.data.redis.core.RedisTemplate},
 *       Jackson-serialized)</li>
 *   <li>{@code stringRedisTemplate-<name>}
 *       ({@link org.springframework.data.redis.core.StringRedisTemplate})</li>
 * </ul>
 * Inject them with {@code @Qualifier}. An empty / absent {@code multi} map means single-Redis
 * mode and is the default.
 */
@ConfigurationProperties(prefix = "integrate-boot.redis")
public class RedisProperties {

    /**
     * Extra Redis instances keyed by a logical name. Each value is a full
     * {@link RedissonConfig}. An empty map (the default) disables multi-instance support.
     */
    private Map<String, RedissonConfig> multi = new LinkedHashMap<>();

    public Map<String, RedissonConfig> getMulti() {
        return multi;
    }

    public void setMulti(Map<String, RedissonConfig> multi) {
        this.multi = multi;
    }
}
