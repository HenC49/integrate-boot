package com.github.henc.integrateboot.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;

/**
 * Last-resort handler for exceptions escaping an {@link AsyncEventListener} (or any other
 * {@code @Async} method): logs the failure with listener and event context, then publishes
 * {@link EventListenerFailedEvent} for interested parties.
 *
 * <p>Recursive failures are cut off: a listener consuming {@link EventListenerFailedEvent}
 * that itself fails is logged but does not spawn further meta-events, so one bad listener
 * cannot cascade.
 */
public class EventBusAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EventBusAsyncExceptionHandler.class);

    private final ApplicationEventPublisher publisher;

    public EventBusAsyncExceptionHandler(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        // For @EventListener methods the first parameter is the event itself.
        Object event = params.length > 0 ? params[0] : null;
        log.error("Async listener {}.{} failed while handling {}",
                method.getDeclaringClass().getName(), method.getName(),
                event != null ? event.getClass().getName() : "(no event)", ex);

        for (Object param : params) {
            if (param instanceof EventListenerFailedEvent) {
                return;
            }
        }
        publisher.publishEvent(new EventListenerFailedEvent(
                method.getDeclaringClass().getName() + "#" + method.getName(), event, ex));
    }
}
