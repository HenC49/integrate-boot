package com.github.henc.integrateboot.event;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the composed {@link AsyncEventListener} annotation end to end at the container
 * level: Spring discovers it as an {@code @EventListener} (meta-annotation) and executes it
 * through the {@code @Async} infrastructure on a worker thread, and a failing async listener
 * surfaces as {@link EventListenerFailedEvent} instead of propagating to the publisher.
 */
class AsyncEventListenerWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    com.github.henc.integrateboot.event.config.EventBusAutoConfiguration.class))
            .withUserConfiguration(ListenersConfig.class);

    @Test
    void composedAnnotationRunsTheListenerAsynchronously() throws Exception {
        runner.run(context -> {
            RecordingListeners listeners = context.getBean(RecordingListeners.class);
            context.getBean(EventBus.class).publish(new SampleEvent("payload"));

            assertThat(listeners.awaitReceived()).isTrue();
            assertThat(listeners.getValues()).containsExactly("payload");
            // @Async semantics: the listener ran on a worker thread, not the publisher's.
            assertThat(listeners.getThreads()).isNotEqualTo(Thread.currentThread().getName());
        });
    }

    @Test
    void failingAsyncListenerSurfacesAsMetaEvent() throws Exception {
        runner.run(context -> {
            RecordingListeners listeners = context.getBean(RecordingListeners.class);
            context.getBean(EventBus.class).publish(new SampleEvent("boom"));

            assertThat(listeners.awaitFailed()).isTrue();
            assertThat(listeners.getFailures()).hasSize(1);
            EventListenerFailedEvent meta = listeners.getFailures().get(0);
            assertThat(meta.listenerMethod()).contains("failOnBoom");
            assertThat(meta.exception()).hasMessageContaining("boom");
        });
    }

    record SampleEvent(String value) {
    }

    @Configuration(proxyBeanMethods = false)
    static class ListenersConfig {

        @Bean
        RecordingListeners recordingListeners() {
            return new RecordingListeners();
        }
    }

    static class RecordingListeners {

        private final CountDownLatch received = new CountDownLatch(1);
        private final CountDownLatch failed = new CountDownLatch(1);
        private final List<String> values = new CopyOnWriteArrayList<>();
        private final List<String> threads = new CopyOnWriteArrayList<>();
        private final List<EventListenerFailedEvent> failures = new CopyOnWriteArrayList<>();

        @AsyncEventListener
        void onSampleEvent(SampleEvent event) {
            values.add(event.value());
            threads.add(Thread.currentThread().getName());
            received.countDown();
        }

        @AsyncEventListener
        void failOnBoom(SampleEvent event) {
            if ("boom".equals(event.value())) {
                throw new IllegalStateException("boom listener");
            }
        }

        @EventListener
        void onListenerFailed(EventListenerFailedEvent event) {
            failures.add(event);
            failed.countDown();
        }

        // State must be read through methods: @EnableAsync proxies the bean, so direct
        // field access from the test would hit the uninitialized proxy instance.
        boolean awaitReceived() throws InterruptedException {
            return received.await(5, TimeUnit.SECONDS);
        }

        boolean awaitFailed() throws InterruptedException {
            return failed.await(5, TimeUnit.SECONDS);
        }

        List<String> getValues() {
            return values;
        }

        List<String> getThreads() {
            return threads;
        }

        List<EventListenerFailedEvent> getFailures() {
            return failures;
        }
    }
}
