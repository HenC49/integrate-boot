package com.github.henc.integrateboot.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shortcut for {@code @EventListener + @Async}: the listener executes asynchronously on
 * the platform's task executor (Boot's {@code applicationTaskExecutor}, virtual-thread
 * backed when {@code spring.threads.virtual.enabled=true}), decoupled from the
 * publisher's thread and transaction.
 *
 * <p>Best-effort semantics: a listener failure does not affect the publisher; it is
 * reported through the error log and the {@link EventListenerFailedEvent} meta-event.
 * Use {@code @TransactionalEventListener} instead when delivery must be tied to the
 * publishing transaction.
 */
@EventListener
@Async
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AsyncEventListener {
}
