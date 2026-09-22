package com.github.henc.integrateboot.lock.config;

import com.github.henc.integrateboot.lock.DefaultDistributedLock;
import com.github.henc.integrateboot.lock.DistributedLock;
import com.github.henc.integrateboot.lock.LockProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the distributed lock layer: as soon as a {@link LockProvider} bean exists, the
 * default {@link DistributedLock} facade is auto-configured on top of it — bean presence
 * is the on/off switch, so a service opts in by bringing (or defining) a provider and
 * needs no extra property. The shipped provider is the Redis-backed
 * {@code RedissonLockProvider} contributed by {@code integrate-boot-redis}.
 *
 * <p>The {@code afterName} ordering (in its string form to avoid a compile dependency on
 * the Redis-backed implementation) makes sure the provider contributed by that
 * auto-configuration is registered before this facade bean's {@code @ConditionalOnBean}
 * check runs. Providers from regular user configuration are order-independent.
 *
 * <p>A service that brings its own facade defines any {@link DistributedLock} bean and
 * the default backs off through {@code @ConditionalOnMissingBean}.
 */
@AutoConfiguration(afterName = "com.github.henc.integrateboot.redis.lock.RedissonLockAutoConfiguration")
@EnableConfigurationProperties(LockProperties.class)
public class LockAutoConfiguration {

    /**
     * Creates the auto-configuration; beans are declared on the factory methods below.
     */
    public LockAutoConfiguration() {
    }

    /**
     * The default facade over the registered {@link LockProvider}: business keys are
     * namespaced with {@code integrate-boot.lock.key-prefix} and requests without
     * explicit durations inherit {@code integrate-boot.lock.default-wait-time} /
     * {@code default-lease-time}.
     *
     * @param provider   the registered lock backend
     * @param properties the layer settings (prefix + duration defaults)
     * @return the default {@link DistributedLock} bean
     */
    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    @ConditionalOnBean(LockProvider.class)
    public DistributedLock distributedLock(LockProvider provider, LockProperties properties) {
        return new DefaultDistributedLock(provider, properties.getKeyPrefix(),
                properties.getDefaultWaitTime(), properties.getDefaultLeaseTime());
    }
}
