package com.github.henc.integrateboot.event;

/**
 * Meta-event published (synchronously) when an asynchronous listener fails, so business
 * code can hook alerting or auditing without touching the platform's exception handling.
 *
 * @param listenerMethod the failing listener, as {@code declaring.Class#methodName}
 * @param event          the event handed to the failing listener (its first argument;
 *                       {@code null} for a no-arg listener)
 * @param exception      the failure raised by the listener
 */
public record EventListenerFailedEvent(String listenerMethod, Object event, Throwable exception) {
}
