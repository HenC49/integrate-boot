package com.github.henc.integrateboot.event;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventBusAsyncExceptionHandlerTest {

    private final List<Object> published = new ArrayList<>();
    private final EventBusAsyncExceptionHandler handler =
            new EventBusAsyncExceptionHandler(published::add);

    private record SampleEvent(String value) {
        void listener(SampleEvent event) {
        }

        void failedListener(EventListenerFailedEvent event) {
        }
    }

    @Test
    void publishesMetaEventWithListenerMethodAndEvent() throws Exception {
        Method method = SampleEvent.class.getDeclaredMethod("listener", SampleEvent.class);
        IllegalStateException failure = new IllegalStateException("boom");

        handler.handleUncaughtException(failure, method, new SampleEvent("payload"));

        assertThat(published).hasSize(1);
        EventListenerFailedEvent meta = (EventListenerFailedEvent) published.get(0);
        assertThat(meta.listenerMethod())
                .isEqualTo(SampleEvent.class.getName() + "#listener");
        assertThat(meta.event()).isEqualTo(new SampleEvent("payload"));
        assertThat(meta.exception()).isSameAs(failure);
    }

    @Test
    void failingMetaEventListenerDoesNotSpawnFurtherMetaEvents() throws Exception {
        Method method = SampleEvent.class.getDeclaredMethod("failedListener", EventListenerFailedEvent.class);
        EventListenerFailedEvent original = new EventListenerFailedEvent("x#y", null, new RuntimeException());

        handler.handleUncaughtException(new IllegalStateException("cascade"), method, original);

        // Logged, but no new EventListenerFailedEvent published.
        assertThat(published).isEmpty();
    }
}
