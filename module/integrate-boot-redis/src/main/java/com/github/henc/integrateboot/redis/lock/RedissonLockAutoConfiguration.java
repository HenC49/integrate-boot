package com.github.henc.integrateboot.redis.lock;

import com.github.henc.integrateboot.lock.LockProvider;
import com.github.henc.integrateboot.lock.config.LockAutoConfiguration;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration of the Redis-backed {@link LockProvider}: contributes a
 * {@link RedissonLockProvider} whenever the Redisson client is configured (the shared
 * client the whole Redis layer already runs on), so the
 * {@code integrate-boot-lock} facade activates as soon as a Redis connection exists —
 * no extra property.
 *
 * <p>Apps without a Redis connection are unaffected: the provider backs off and the lock
 * facade (whose {@code @ConditionalOnBean(LockProvider.class)} gate this ordering feeds)
 * stays absent. A user-defined {@link LockProvider} bean replaces the default through
 * {@code @ConditionalOnMissingBean}.
 *
 * <p>The {@code before} hint keeps this provider ahead of {@code LockAutoConfiguration}
 * in the auto-configuration order, which is what lets that facade see this bean.
 */
@AutoConfiguration(before = LockAutoConfiguration.class, after = RedissonAutoConfigurationV4.class)
@ConditionalOnClass({RedissonClient.class, LockProvider.class})
public class RedissonLockAutoConfiguration {

    /**
     * Creates the auto-configuration; beans are declared on the factory methods below.
     */
    public RedissonLockAutoConfiguration() {
    }

    /**
     * The Redis-backed provider over the shared Redisson client.
     *
     * @param redissonClient the client shared with the rest of the Redis layer
     * @return the default {@link LockProvider} bean
     */
    @Bean
    @ConditionalOnMissingBean(LockProvider.class)
    @ConditionalOnBean(RedissonClient.class)
    public LockProvider redissonLockProvider(RedissonClient redissonClient) {
        return new RedissonLockProvider(redissonClient);
    }
}
