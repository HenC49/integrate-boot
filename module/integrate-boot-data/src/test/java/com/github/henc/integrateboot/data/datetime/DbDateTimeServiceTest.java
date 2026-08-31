package com.github.henc.integrateboot.data.datetime;

import com.github.henc.integrateboot.base.util.DateUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the database time source against a real (embedded H2) database, plus its
 * unavailable-fallback behavior when the database cannot be reached.
 */
class DbDateTimeServiceTest {

    private static JdbcTemplate h2JdbcTemplate() {
        return new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build());
    }

    @Test
    void readsDatabaseClockRoundedToSeconds() {
        DbDateTimeService service = new DbDateTimeService(h2JdbcTemplate());

        Date date = service.getCurrentDate();

        // H2 runs in the same JVM, so its clock is the local clock — allow a few seconds
        // of slack for the query round trip.
        assertThat(date).isCloseTo(new Date(), 10_000L);
        // now() is second-granular; sub-second noise must be rounded away.
        assertThat(date.getTime() % 1000).isZero();
    }

    @Test
    void customQueryIsUsed() {
        DbDateTimeService service = new DbDateTimeService(h2JdbcTemplate(),
                "select timestamp '2024-01-01 08:30:00'");

        assertThat(service.getCurrentDate()).isEqualTo(
                DateUtils.parseDate("2024-01-01 08:30:00", DateUtils.DATE_TIME_FORMAT));
    }

    @Test
    void unreachableDatabaseYieldsNullInsteadOfThrowing() {
        // No driver for this URL prefix — connection setup fails immediately.
        JdbcTemplate broken = new JdbcTemplate(
                new DriverManagerDataSource("jdbc:no-such-driver:db", "sa", ""));
        DbDateTimeService service = new DbDateTimeService(broken);

        assertThat(service.getCurrentDate()).isNull();
    }

    @Test
    void brokenQueryYieldsNullInsteadOfThrowing() {
        DbDateTimeService service = new DbDateTimeService(h2JdbcTemplate(),
                "select * from no_such_table");

        assertThat(service.getCurrentDate()).isNull();
    }

    @Test
    void sourceMetadata() {
        DbDateTimeService service = new DbDateTimeService(h2JdbcTemplate());

        assertThat(service.getType()).isEqualTo(DbDateTimeService.TYPE);
        assertThat(service.getType()).isEqualTo("db");
        // Second-granular now() opts out of offset caching; priority sits below custom
        // sources (0) and the redis source (100).
        assertThat(service.useInterval()).isFalse();
        assertThat(service.getOrder()).isEqualTo(200);
    }
}
