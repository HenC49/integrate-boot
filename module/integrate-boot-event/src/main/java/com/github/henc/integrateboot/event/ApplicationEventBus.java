package com.github.henc.integrateboot.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Default {@link EventBus} implementation: a thin, logging facade over Spring's
 * {@link ApplicationEventPublisher}. No wrapping, no copying — listeners receive the
 * exact object handed to {@link #publish(Object)}.
 */
public class ApplicationEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventBus.class);

    private final ApplicationEventPublisher publisher;

    public ApplicationEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Object event) {
        if (log.isDebugEnabled()) {
            log.debug("Publishing event of type {}", event.getClass().getName());
        }
        publisher.publishEvent(event);
    }
}
