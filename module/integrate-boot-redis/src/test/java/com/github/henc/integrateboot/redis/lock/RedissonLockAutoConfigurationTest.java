package com.github.henc.integrateboot.redis.lock;

import com.github.henc.integrateboot.lock.LockProvider;
import com.github.henc.integrateboot.lock.LockRequest;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Activation rules of {@link RedissonLockAutoConfiguration}: a configured
 * {@code RedissonClient} switches the Redis-backed provider on, no client means no
 * provider (and in turn no lock facade), and a user-defined provider replaces the
 * default.
 */
class RedissonLockAutoConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class CustomProviderConfig {

        @Bean
        LockProvider customLockProvider() {
            return request -> Optional.empty();
        }
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedissonLockAutoConfiguration.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class));
    }

    @Test
    void noProviderWithoutARedissonClient() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedissonLockAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(LockProvider.class));
    }

    @Test
    void redissonClientActivatesTheRedisBackedProvider() {
        runner().run(context -> {
            assertThat(context).hasSingleBean(LockProvider.class);
            assertThat(context.getBean(LockProvider.class)).isInstanceOf(RedissonLockProvider.class);
        });
    }

    @Test
    void customProviderBeanMakesTheDefaultBackOff() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedissonLockAutoConfiguration.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .withUserConfiguration(CustomProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(LockProvider.class);
                    assertThat(context.getBean(LockProvider.class))
                            .isSameAs(context.getBean(CustomProviderConfig.class).customLockProvider());
                });
    }
}
