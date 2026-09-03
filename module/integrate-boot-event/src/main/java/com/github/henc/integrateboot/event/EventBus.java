package com.github.henc.integrateboot.event;

/**
 * Facade over Spring's application event mechanism for in-process, module-decoupling
 * events: publishers stay unaware of who listens, how many listeners exist, or whether
 * they run synchronously or asynchronously.
 *
 * <p>Events are plain application objects (records recommended); no base class or envelope
 * is required. Delivery semantics are chosen by the <em>listener</em>, not the publisher:
 *
 * <ul>
 *   <li>{@code @EventListener} — synchronous, participates in the publisher's transaction;</li>
 *   <li>{@link AsyncEventListener} — asynchronous, best-effort;</li>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)} — runs after the publishing
 *       transaction commits; when the optional reliability layer is enabled (see the
 *       module README), such listeners are additionally backed by a transactional outbox
 *       that survives listener failures and application crashes.</li>
 * </ul>
 */
public interface EventBus {

    /**
     * Publish a single event to all matching listeners.
     *
     * @param event the event payload, ideally an immutable record
     */
    void publish(Object event);

    /**
     * Publish several events, in declaration order.
     */
    default void publishAll(Object... events) {
        for (Object event : events) {
            publish(event);
        }
    }
}
