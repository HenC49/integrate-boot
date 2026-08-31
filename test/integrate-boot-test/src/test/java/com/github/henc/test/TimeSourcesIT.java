package com.github.henc.test;

import com.github.henc.integrateboot.base.util.DateUtils;
import com.github.henc.integrateboot.base.datetime.DateTimeRegistry;
import com.github.henc.integrateboot.base.datetime.DateTimeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end wiring of the datetime sources: with the full stack booted (H2 datasource,
 * Redis connection), the modules' time sources are registered and
 * {@link DateUtils#getCurrentDateTime()} answers from the best available one — whichever
 * it is (redis, db, or the system-clock fallback), it must track the real clock and must
 * never throw.
 *
 * <p>Named {@code TimeSources...} rather than {@code DateTime...} on purpose: JUnit runs
 * test classes in name order, and this default-profile context must start
 * <em>after</em> {@code DynamicDataSourceIT}'s context — see the ordering note on that
 * class.
 */
@SpringBootTest
class TimeSourcesIT {

    @Test
    void moduleSourcesAreRegistered() {
        assertThat(DateTimeRegistry.getRegisteredServices())
                .extracting(DateTimeService::getType)
                .contains("db", "redis");
    }

    @Test
    void currentDateTimeTracksTheRealClock() {
        assertThat(DateUtils.getCurrentDateTime()).isCloseTo(new Date(), 10_000L);
        assertThat(DateUtils.getCurrentDateTimeSimpleInterval()).isCloseTo(new Date(), 10_000L);
        assertThat(DateUtils.getDateFromDateTimeService()).isCloseTo(new Date(), 10_000L);
        assertThat(DateUtils.getSystemDateTime()).isCloseTo(new Date(), 10_000L);
    }

    @Test
    void repeatedReadsStayCheapAndConsistent() {
        Date first = DateUtils.getCurrentDateTimeSimpleInterval();

        for (int i = 0; i < 1000; i++) {
            DateUtils.getCurrentDateTimeSimpleInterval();
        }

        // The cached-offset path must not drift away from the real clock.
        assertThat(DateUtils.getCurrentDateTimeSimpleInterval()).isCloseTo(first, 10_000L);
    }
}
