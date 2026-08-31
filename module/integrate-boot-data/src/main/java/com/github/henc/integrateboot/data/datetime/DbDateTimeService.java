package com.github.henc.integrateboot.data.datetime;

import com.github.henc.integrateboot.base.datetime.DateTimeService;
import com.github.henc.integrateboot.base.util.DateUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.Date;

/**
 * The {@code "db"} time source for {@link DateUtils}:
 * the database server's clock, read with {@code select now()} (understood by MySQL,
 * PostgreSQL, H2 and friends — register an instance with a custom query for databases
 * that spell it differently, e.g. {@code select getdate()}).
 *
 * <p>Registered automatically by {@code integrate-boot-data} when a {@link DataSource}
 * is configured. The database's {@code now()} is second-granular, so the source opts
 * out of offset caching ({@link #useInterval()} is {@code false});
 * {@link DateUtils#getCurrentDateTimeSimpleInterval()}
 * offers the cached read path for it. A connection or query problem makes
 * {@link #getCurrentDate()} return {@code null} — a fallback signal, never an outage:
 * {@link com.github.henc.integrateboot.base.datetime.DateTimeRegistry} then answers from
 * another source or the system clock.
 */
public class DbDateTimeService implements DateTimeService {

    /** Source identifier: {@value}. */
    public static final String TYPE = "db";

    /** Priority: below custom sources (0) and {@code redis} (100). */
    public static final int ORDER = 200;

    /** Default query, understood by MySQL / PostgreSQL / H2 and most other databases. */
    public static final String DEFAULT_QUERY = "select now()";

    private final JdbcTemplate jdbcTemplate;

    private final String query;

    public DbDateTimeService(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), DEFAULT_QUERY);
    }

    public DbDateTimeService(DataSource dataSource, String query) {
        this(new JdbcTemplate(dataSource), query);
    }

    public DbDateTimeService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_QUERY);
    }

    public DbDateTimeService(JdbcTemplate jdbcTemplate, String query) {
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public boolean useInterval() {
        return false;
    }

    @Override
    public Date getCurrentDate() {
        try {
            Timestamp timestamp = jdbcTemplate.queryForObject(query, Timestamp.class);
            if (timestamp == null) {
                return null;
            }
            // now() is second-granular on most databases — round away sub-second noise
            // instead of pretending to know the milliseconds.
            long millis = BigDecimal.valueOf(timestamp.getTime() / 1000d)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue() * 1000;
            return new Date(millis);
        } catch (DataAccessException e) {
            // Database unreachable / query broken — let the registry fall back.
            return null;
        }
    }

}
