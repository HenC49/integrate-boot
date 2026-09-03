package com.github.henc.test;

import com.github.henc.integrateboot.event.AsyncEventListener;
import com.github.henc.integrateboot.event.EventBus;
import com.github.henc.integrateboot.event.EventListenerFailedEvent;
import com.github.henc.test.event.UserCreated;
import com.github.henc.test.event.listener.UserEventListeners;
import com.github.henc.test.user.entity.User;
import com.github.henc.test.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end wiring of the event bus in the full application: services publish plain
 * records through {@link EventBus}, listeners pick their own delivery contract
 * (best-effort async vs. AFTER_COMMIT), and a failing async listener surfaces as
 * {@link EventListenerFailedEvent} instead of disturbing the publisher.
 *
 * <p>Uses a dedicated in-memory database: the committed-create test case persists its user
 * for real (no test-managed rollback, or AFTER_COMMIT would never fire), which must not
 * leak into the shared database of the other suites.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:eventbusdb;DB_CLOSE_DELAY=-1;MODE=MySQL")
class EventBusIT {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private UserService userService;

    @Autowired
    private UserEventListeners userEventListeners;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FailureListeners failureListeners;

    @Test
    void committedCreateReachesAsyncAndAfterCommitListeners() {
        userService.create(new User("event-dave", 41));

        assertThat(awaitUntil(() -> userEventListeners.getAsyncObserved().contains("event-dave")))
                .as("async listener observes the event")
                .isTrue();
        assertThat(awaitUntil(() -> userEventListeners.getAfterCommitObserved().contains("event-dave")))
                .as("AFTER_COMMIT listener observes the event once the service transaction commits")
                .isTrue();
    }

    @Test
    void rolledBackCreateNeverReachesAfterCommitListeners() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            // Joins the programmatic transaction, which is then rolled back.
            userService.create(new User("event-rogue", 99));
            status.setRollbackOnly();
        });

        // The best-effort async listener observes the event regardless of the transaction
        // outcome — proving the event was actually published and dispatched.
        assertThat(awaitUntil(() -> userEventListeners.getAsyncObserved().contains("event-rogue")))
                .isTrue();
        // The transaction-bound listener must never see a rolled-back creation.
        assertThat(userEventListeners.getAfterCommitObserved()).doesNotContain("event-rogue");
    }

    @Test
    void failingAsyncListenerSurfacesAsMetaEvent() throws Exception {
        eventBus.publish(new PingedEvent("ping-1"));

        assertThat(failureListeners.awaitFailed()).isTrue();
        EventListenerFailedEvent meta = failureListeners.getFailures().get(0);
        assertThat(meta.listenerMethod()).contains("failOnPing");
        assertThat(meta.event()).isEqualTo(new PingedEvent("ping-1"));
        assertThat(meta.exception()).hasMessageContaining("planned failure");
    }

    record PingedEvent(String token) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureWiring {

        @Bean
        FailureListeners failureListeners() {
            return new FailureListeners();
        }
    }

    static class FailureListeners {

        private final CountDownLatch failed = new CountDownLatch(1);
        private final List<EventListenerFailedEvent> failures = new CopyOnWriteArrayList<>();

        @AsyncEventListener
        void failOnPing(PingedEvent event) {
            throw new IllegalStateException("planned failure");
        }

        @EventListener
        void onListenerFailed(EventListenerFailedEvent event) {
            failures.add(event);
            failed.countDown();
        }

        // Read through methods: @EnableAsync proxies the bean, so direct field access from
        // the test would hit the uninitialized proxy instance.
        boolean awaitFailed() throws InterruptedException {
            return failed.await(5, TimeUnit.SECONDS);
        }

        List<EventListenerFailedEvent> getFailures() {
            return failures;
        }
    }

    private static boolean awaitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
