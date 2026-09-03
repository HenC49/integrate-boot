package com.github.henc.test;

import com.github.henc.integrateboot.event.EventBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the optional reliability layer: with the Modulith artifacts on the classpath and
 * {@code integrate-boot.event.reliability.enabled=true}, a {@code @ApplicationModuleListener}
 * delivery that fails is persisted in the transactional outbox and gets re-delivered on
 * resubmission — the "at least once" contract the design promises.
 *
 * <p><b>Isolation.</b> The registry creates its table in the shared H2 database and boots
 * Modulith infrastructure, so this class lives in the {@code reliability} source set and
 * runs in its own JVM through the dedicated {@code reliabilityTest} Gradle task (opt-in,
 * not part of {@code build}; run it via {@code task reliability-test}).
 */
@SpringBootTest
@ActiveProfiles("reliability")
class EventReliabilityIT {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private IncompleteEventPublications incompletePublications;

    @Autowired
    private OrderPlacedListeners listeners;

    @Test
    void failedDeliveryIsStoredAndRedeliveredOnResubmission() {
        // Publish inside a committed transaction: the registry stores the publication in
        // the same transaction (the outbox), then hands it to the listener after commit.
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(
                status -> eventBus.publish(new OrderPlaced("reliability-order-1")));

        // The first delivery is programmed to fail; the listener must NOT have completed it.
        assertThat(awaitUntil(() -> listeners.getAttempts() >= 1)).isTrue();
        assertThat(listeners.getCompleted()).doesNotContain("reliability-order-1");

        // The incomplete publication is on record; flip the listener to succeed and
        // re-deliver — the delivery now completes.
        listeners.succeedFromNowOn();
        incompletePublications.resubmitIncompletePublications(publication -> true);

        assertThat(awaitUntil(() -> listeners.getCompleted().contains("reliability-order-1")))
                .as("re-delivered publication completes the listener")
                .isTrue();
    }

    record OrderPlaced(String orderNo) {
    }

    // Registered through a test configuration: @IntegrateBoot only component-scans the
    // conventional layer packages, and the enclosing test package is not one of them.
    @TestConfiguration(proxyBeanMethods = false)
    static class ReliabilityWiring {

        @Bean
        OrderPlacedListeners orderPlacedListeners() {
            return new OrderPlacedListeners();
        }
    }

    static class OrderPlacedListeners {

        private final AtomicBoolean failDeliveries = new AtomicBoolean(true);
        private final List<String> completed = new CopyOnWriteArrayList<>();
        private volatile int attempts;

        // @ApplicationModuleListener = @Async + @Transactional(REQUIRES_NEW) +
        // @TransactionalEventListener: the registry-backed durable variant.
        @ApplicationModuleListener
        public void onOrderPlaced(OrderPlaced event) {
            attempts++;
            if (failDeliveries.get()) {
                throw new IllegalStateException("planned first-delivery failure");
            }
            completed.add(event.orderNo());
        }

        void succeedFromNowOn() {
            failDeliveries.set(false);
        }

        int getAttempts() {
            return attempts;
        }

        List<String> getCompleted() {
            return completed;
        }
    }

    private static boolean awaitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + 10_000_000_000L;
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
