package com.github.henc.integrateboot.redis.datetime;

import com.github.henc.integrateboot.base.datetime.DateTimeRegistry;
import com.github.henc.integrateboot.base.datetime.DateTimeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Context-level tests: the Redis time source is contributed when a Redis connection
 * factory exists, honors user overrides, is bridged into the static
 * {@link DateTimeRegistry} (and removed again when the context closes), and applies the
 * shared {@code integrate-boot.datetime.*} settings. No live Redis is needed — the
 * factory is mocked; only bean wiring is exercised here.
 */
class DateTimeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DateTimeAutoConfiguration.class));

    /**
     * The minimum Redis infrastructure the auto-configuration reacts to: a connection
     * factory bean plus a template built on it (a bare template would fail its own
     * afterPropertiesSet, which requires a factory).
     */
    private ApplicationContextRunner withRedisInfrastructure() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        return runner
                .withBean("redisConnectionFactory", RedisConnectionFactory.class, () -> connectionFactory)
                .withBean("stringRedisTemplate", StringRedisTemplate.class,
                        () -> new StringRedisTemplate(connectionFactory));
    }

    @Test
    void backsOffWithoutRedisConnectionFactory() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(RedisDateTimeService.class);
            // The registrar still exists — it sweeps up any user-defined source beans.
            assertThat(context).hasBean("redisDateTimeServiceRegistrar");
        });
    }

    @Test
    void contributesAndRegistersSourceWithConnectionFactory() {
        withRedisInfrastructure().run(context -> {
            assertThat(context).hasSingleBean(RedisDateTimeService.class);
            assertThat(DateTimeRegistry.getRegisteredServices())
                    .anySatisfy(service -> assertThat(service.getType()).isEqualTo("redis"));
        });

        // Context closed — the source must be unregistered again.
        assertThat(DateTimeRegistry.getRegisteredServices())
                .noneMatch(service -> "redis".equals(service.getType()));
    }

    @Test
    void userDefinedSourceReplacesTheDefault() {
        RedisDateTimeService custom = new RedisDateTimeService(
                new StringRedisTemplate(mock(RedisConnectionFactory.class)));

        withRedisInfrastructure()
                .withBean("customRedisDateTimeService", RedisDateTimeService.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisDateTimeService.class);
                    assertThat(context.getBean(RedisDateTimeService.class)).isSameAs(custom);
                });
    }

    @Test
    void sharedDatetimeSettingsAreApplied() {
        withRedisInfrastructure()
                .withPropertyValues("integrate-boot.datetime.prefer=redis")
                .run(context -> assertThat(DateTimeRegistry.getPreferredType()).isEqualTo("redis"));

        // Defaults restored on context close.
        assertThat(DateTimeRegistry.getPreferredType()).isNull();
    }

    @Test
    void customSourceBeansAreRegisteredToo() {
        DateTimeService custom = new DateTimeService() {
            @Override
            public String getType() {
                return "satellite";
            }

            @Override
            public java.util.Date getCurrentDate() {
                return new java.util.Date();
            }
        };

        withRedisInfrastructure()
                .withBean("satelliteService", DateTimeService.class, () -> custom)
                .run(context -> assertThat(DateTimeRegistry.getRegisteredServices()).contains(custom));
    }
}
