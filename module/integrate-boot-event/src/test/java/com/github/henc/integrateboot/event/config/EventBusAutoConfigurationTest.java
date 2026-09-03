package com.github.henc.integrateboot.event.config;

import com.github.henc.integrateboot.event.EventBus;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration level wiring: the bus is on by default, the unified async takeover can
 * be declined, and application-provided async configuration is never overridden.
 */
class EventBusAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventBusAutoConfiguration.class));

    @Test
    void busAndAsyncTakeoverAreOnByDefault() {
        runner.run(context -> {
            assertThat(context).hasBean("eventBus");
            assertThat(context.getBean(EventBus.class)).isNotNull();
            assertThat(context).hasSingleBean(AsyncConfigurer.class);
        });
    }

    @Test
    void masterSwitchOffRemovesEverything() {
        runner.withPropertyValues("integrate-boot.event.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(EventBus.class));
    }

    @Test
    void asyncTakeoverCanBeDeclinedIndependently() {
        runner.withPropertyValues("integrate-boot.event.async.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventBus.class);
                    assertThat(context).doesNotHaveBean(AsyncConfigurer.class);
                });
    }

    @Test
    void applicationAsyncConfigurerWins() {
        runner.withUserConfiguration(CustomAsyncConfigurer.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncConfigurer.class);
                    assertThat(context.getBean(AsyncConfigurer.class))
                            .isInstanceOf(CustomAsyncConfigurer.class);
                });
    }

    @Test
    void reliabilitySwitchIsInertWithoutModulithOnTheClasspath() {
        // The Modulith artifacts are compileOnly in this module, so they are absent here on
        // purpose: flipping the reliability switch must not break startup, the glue simply
        // stays dormant until the artifacts are added explicitly.
        runner.withPropertyValues("integrate-boot.event.reliability.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EventBus.class);
                });
    }

    static class CustomAsyncConfigurer extends AsyncConfigurerSupport {

        @Override
        public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
            return (ex, method, params) -> {
            };
        }
    }
}
