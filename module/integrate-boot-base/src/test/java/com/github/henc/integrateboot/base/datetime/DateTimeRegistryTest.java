package com.github.henc.integrateboot.base.datetime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Behavioral tests of the registry engine: source selection by preference / priority,
 * fallback when sources fail, offset caching, and the simple-interval read path. Each
 * test drives the registry through fake services with controllable clocks.
 */
class DateTimeRegistryTest {

    /** Fake source with a clock running at a fixed offset from the local clock. */
    private static final class FakeService implements DateTimeService {
        private final String type;
        private final int order;
        private final boolean useInterval;
        private final LongSupplier offsetMillis;
        private final AtomicLong calls = new AtomicLong();
        private boolean fail;

        FakeService(String type, int order, boolean useInterval, long offsetMillis) {
            this(type, order, useInterval, () -> offsetMillis);
        }

        FakeService(String type, int order, boolean useInterval, LongSupplier offsetMillis) {
            this.type = type;
            this.order = order;
            this.useInterval = useInterval;
            this.offsetMillis = offsetMillis;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public boolean useInterval() {
            return useInterval;
        }

        @Override
        public Date getCurrentDate() {
            calls.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("source down");
            }
            return new Date(System.currentTimeMillis() + offsetMillis.getAsLong());
        }
    }

    @AfterEach
    void cleanUp() {
        DateTimeRegistry.reset();
    }

    @Test
    void noRegistrationFallsBackToSystemClock() {
        Date now = DateTimeRegistry.getCurrentDateTime();

        assertThat(now).isCloseTo(new Date(), 5_000L);
        assertThat(DateTimeRegistry.getDateFromService()).isCloseTo(new Date(), 5_000L);
    }

    @Test
    void picksSourceByPriorityOrder() {
        FakeService lowPriority = new FakeService("db", 200, false, 0);
        FakeService highPriority = new FakeService("redis", 100, false, 0);
        DateTimeRegistry.register(lowPriority);
        DateTimeRegistry.register(highPriority);

        assertThat(DateTimeRegistry.usableService()).isSameAs(highPriority);
    }

    @Test
    void preferredTypeOutranksPriority() {
        FakeService redis = new FakeService("redis", 100, false, 0);
        FakeService db = new FakeService("db", 200, false, 0);
        DateTimeRegistry.register(redis);
        DateTimeRegistry.register(db);
        DateTimeRegistry.setPreferredType("db");

        assertThat(DateTimeRegistry.usableService()).isSameAs(db);
    }

    @Test
    void preferredTypeServerUsesSystemClock() {
        DateTimeRegistry.register(new FakeService("redis", 100, true, 0));
        DateTimeRegistry.setPreferredType(DateTimeRegistry.TYPE_SERVER);

        assertThat(DateTimeRegistry.usableService()).isInstanceOf(SystemDateTimeService.class);
    }

    @Test
    void unavailablePreferredFallsBackByPriority() {
        FakeService broken = new FakeService("redis", 100, true, 0);
        broken.fail = true;
        FakeService working = new FakeService("db", 200, false, 0);
        DateTimeRegistry.register(broken);
        DateTimeRegistry.register(working);
        DateTimeRegistry.setPreferredType("redis");

        assertThat(DateTimeRegistry.usableService()).isSameAs(working);
    }

    @Test
    void failingDirectServiceFallsBackToSystemClock() {
        FakeService broken = new FakeService("db", 200, false, 0);
        DateTimeRegistry.register(broken);
        // First read resolves the source successfully.
        assertThat(DateTimeRegistry.getCurrentDateTime()).isCloseTo(new Date(), 5_000L);

        // The source breaks — subsequent reads must still answer, from the system clock.
        broken.fail = true;

        assertThat(DateTimeRegistry.getCurrentDateTime()).isCloseTo(new Date(), 5_000L);
        assertThat(DateTimeRegistry.getCurrentDateTimeSimpleInterval())
                .isCloseTo(new Date(), 5_000L);
    }

    @Test
    void unregisteringWinnerReResolvesToNextSource() {
        FakeService redis = new FakeService("redis", 100, false, 0);
        FakeService db = new FakeService("db", 200, false, 0);
        DateTimeRegistry.register(redis);
        DateTimeRegistry.register(db);
        assertThat(DateTimeRegistry.usableService()).isSameAs(redis);

        DateTimeRegistry.unregister(redis);

        assertThat(DateTimeRegistry.usableService()).isSameAs(db);
        assertThat(DateTimeRegistry.getRegisteredServices()).containsExactly(db);
    }

    @Test
    void registerIsIdempotentPerInstance() {
        FakeService redis = new FakeService("redis", 100, false, 0);
        DateTimeRegistry.register(redis);
        DateTimeRegistry.register(redis);

        assertThat(DateTimeRegistry.getRegisteredServices()).hasSize(1);
    }

    @Test
    void intervalModeAppliesSourceOffsetWithoutRemoteCalls() {
        long skew = Duration.ofHours(2).toMillis();
        FakeService skewed = new FakeService("redis", 100, true, skew);
        DateTimeRegistry.register(skewed);

        Date current = DateTimeRegistry.getCurrentDateTime();

        // The served time carries the source's +2h offset...
        assertThat(current.getTime() - System.currentTimeMillis()).isCloseTo(skew, within(5_000L));
        // ...and further reads come from the cached offset, not from the source.
        long callsAfterFirstRead = skewed.calls.get();
        DateTimeRegistry.getCurrentDateTime();
        DateTimeRegistry.getCurrentDateTime();
        assertThat(skewed.calls.get()).isEqualTo(callsAfterFirstRead);
    }

    @Test
    void intervalDisabledReadsDirectlyOnEveryCall() {
        FakeService direct = new FakeService("db", 200, false, Duration.ofMinutes(1).toMillis());
        DateTimeRegistry.register(direct);
        DateTimeRegistry.setIntervalEnabled(false);

        DateTimeRegistry.getCurrentDateTime();
        long calls = direct.calls.get();
        DateTimeRegistry.getCurrentDateTime();

        assertThat(direct.calls.get()).isGreaterThan(calls);
    }

    @Test
    void simpleIntervalCallsSourceAtMostOncePerCheckInterval() {
        FakeService direct = new FakeService("db", 200, false, 0);
        DateTimeRegistry.register(direct);
        DateTimeRegistry.setCheckIntervalMillis(Duration.ofMinutes(10).toMillis());

        DateTimeRegistry.getCurrentDateTimeSimpleInterval();
        long callsAfterFirst = direct.calls.get();
        DateTimeRegistry.getCurrentDateTimeSimpleInterval();
        DateTimeRegistry.getCurrentDateTimeSimpleInterval();

        // Reads within the check interval interpolate locally instead of calling the source.
        assertThat(direct.calls.get()).isEqualTo(callsAfterFirst);
        // And the served time still tracks the real clock.
        assertThat(DateTimeRegistry.getCurrentDateTimeSimpleInterval())
                .isCloseTo(new Date(), 5_000L);
    }

    @Test
    void strictReadAlwaysCallsTheSource() {
        FakeService direct = new FakeService("db", 200, false, Duration.ofMinutes(1).toMillis());
        DateTimeRegistry.register(direct);

        Date strict = DateTimeRegistry.getDateFromService();

        // A non-interval source answers with its own (here skewed) clock on every strict read.
        assertThat(strict.getTime() - System.currentTimeMillis())
                .isCloseTo(Duration.ofMinutes(1).toMillis(), within(5_000L));
    }

    @Test
    void exceptionFromSourceIsTreatedAsUnavailable() {
        FakeService broken = new FakeService("redis", 100, true, 0);
        broken.fail = true;
        FakeService working = new FakeService("db", 200, false, 0);
        DateTimeRegistry.register(broken);
        DateTimeRegistry.register(working);

        assertThat(DateTimeRegistry.getCurrentDateTime()).isCloseTo(new Date(), 5_000L);
    }

    @Test
    void allSourcesDownAnswersFromSystemClock() {
        FakeService broken = new FakeService("redis", 100, true, 0);
        broken.fail = true;
        FakeService brokenToo = new FakeService("db", 200, false, 0);
        brokenToo.fail = true;
        DateTimeRegistry.register(broken);
        DateTimeRegistry.register(brokenToo);

        assertThat(DateTimeRegistry.getCurrentDateTime()).isCloseTo(new Date(), 5_000L);
        assertThat(DateTimeRegistry.getCurrentDateTimeSimpleInterval())
                .isCloseTo(new Date(), 5_000L);
    }

    @Test
    void customServiceOutranksShippedPrioritiesByDefaultOrder() {
        // Default order is 0 — a custom source registers itself to win.
        FakeService custom = new FakeService("satellite", 0, false, 0);
        FakeService shipped = new FakeService("redis", 100, true, 0);
        DateTimeRegistry.register(shipped);
        DateTimeRegistry.register(custom);

        assertThat(DateTimeRegistry.usableService()).isSameAs(custom);
    }

    @Test
    void preferredTypeNotRegisteredFallsBackByPriority() {
        DateTimeRegistry.register(new FakeService("redis", 100, false, 0));
        DateTimeRegistry.setPreferredType("ntp");

        assertThat(DateTimeRegistry.usableService().getType()).isEqualTo("redis");
    }

    @Test
    void registeredServicesSnapshotReflectsRegistrations() {
        FakeService redis = new FakeService("redis", 100, true, 0);
        FakeService db = new FakeService("db", 200, false, 0);
        DateTimeRegistry.register(redis);
        DateTimeRegistry.register(db);

        List<DateTimeService> services = DateTimeRegistry.getRegisteredServices();

        assertThat(services).containsExactlyInAnyOrder(redis, db);
    }
}
