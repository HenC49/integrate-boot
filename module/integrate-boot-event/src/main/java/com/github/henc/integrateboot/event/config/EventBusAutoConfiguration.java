package com.github.henc.integrateboot.event.config;

import com.github.henc.integrateboot.event.ApplicationEventBus;
import com.github.henc.integrateboot.event.EventBus;
import com.github.henc.integrateboot.event.EventBusAsyncExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Wires the in-process event bus: the {@link EventBus} facade over Spring's native event
 * mechanism plus, unless declined, a unified {@code @EnableAsync} takeover with failure
 * handling for async listeners.
 *
 * <p>Ordered before Boot's {@code TaskExecutionAutoConfiguration} on purpose: Boot 4 wraps
 * an application-provided {@link org.springframework.scheduling.annotation.AsyncConfigurer}
 * (executor falls back to {@code applicationTaskExecutor}, exception handler fully
 * delegated). Without the ordering, Boot's own configurer would register first and this
 * module's {@code @ConditionalOnMissingBean} would silently give up the failure handling.
 */
@AutoConfiguration
@AutoConfigureBefore(TaskExecutionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "integrate-boot.event", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EventBusProperties.class)
public class EventBusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventBus.class)
    EventBus eventBus(ApplicationEventPublisher publisher) {
        return new ApplicationEventBus(publisher);
    }

    /**
     * The platform owns {@code @EnableAsync}: applications get asynchronous listeners (and
     * {@code @Async} in general) without writing their own async configuration. The
     * {@link AsyncConfigurer} only contributes the uncaught-exception handler; the executor
     * stays Boot's {@code applicationTaskExecutor}.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @ConditionalOnProperty(prefix = "integrate-boot.event.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class AsyncConfiguration {

        @Bean
        @ConditionalOnMissingBean(AsyncConfigurer.class)
        AsyncConfigurer eventBusAsyncConfigurer(ApplicationEventPublisher publisher) {
            return new EventBusAsyncConfigurer(new EventBusAsyncExceptionHandler(publisher));
        }
    }

    /**
     * Startup note for the reliability layer: the Modulith artifacts are present and the
     * opt-in switch is on, so {@code @TransactionalEventListener} deliveries are persisted
     * in the same transaction as the business data and incomplete ones re-delivered after
     * restart. Recommended Modulith defaults (registry table bootstrap, restart re-delivery)
     * are contributed by {@link ReliabilityDefaultsEnvironmentPostProcessor}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(EventPublicationRegistry.class)
    @ConditionalOnProperty(prefix = "integrate-boot.event.reliability", name = "enabled", havingValue = "true")
    static class ReliabilityConfiguration {

        private static final Logger log = LoggerFactory.getLogger(ReliabilityConfiguration.class);

        ReliabilityConfiguration() {
            log.info("integrate-boot event reliability layer active: Spring Modulith event publication "
                    + "registry (transactional outbox) backs @TransactionalEventListener deliveries");
        }
    }
}
