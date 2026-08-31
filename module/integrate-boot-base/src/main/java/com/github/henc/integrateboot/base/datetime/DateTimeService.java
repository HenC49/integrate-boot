package com.github.henc.integrateboot.base.datetime;

import com.github.henc.integrateboot.base.util.DateUtils;

import java.util.Date;

/**
 * A source of the "current" time — the SPI behind
 * {@link DateUtils#getCurrentDateTime()}.
 *
 * <p>Out of the box integrate-boot ships three sources:
 * <ul>
 *   <li>{@code "redis"} — {@code RedisDateTimeService} in {@code integrate-boot-redis}
 *       (Redis {@code TIME} command),</li>
 *   <li>{@code "db"} — {@code DbDateTimeService} in {@code integrate-boot-data}
 *       ({@code select now()}),</li>
 *   <li>{@code "server"} — {@link SystemDateTimeService}, the local system clock.</li>
 * </ul>
 *
 * <p>An implementation signals <em>unavailability</em> (Redis down, datasource gone, ...)
 * either by returning {@code null} from {@link #getCurrentDate()} or by throwing — the
 * registry treats both the same and falls back to the next source, ultimately to the
 * system clock. It should therefore never propagate a connectivity problem to its caller.
 */
public interface DateTimeService {

    /**
     * Identifier of this source, e.g. {@code "redis"}, {@code "db"} or {@code "server"}.
     * Must be unique among the registered services; {@link DateTimeRegistry#setPreferredType}
     * selects a source by this value.
     *
     * @return the source identifier
     */
    String getType();

    /**
     * Priority used when picking a source and no explicit preference is configured:
     * <em>lower values win</em>. Custom services default to {@code 0}, which outranks the
     * shipped {@code redis} (100) and {@code db} (200) sources — a service you register
     * yourself is presumed to be the one you want.
     *
     * @return the priority, lower values winning
     */
    default int getOrder() {
        return 0;
    }

    /**
     * Whether the registry may cache this source's offset from the local clock and
     * interpolate locally instead of calling it on every read. Suitable for sources
     * that are cheap but not free and whose time has (at least) millisecond precision,
     * e.g. Redis. Sources with coarse precision (a database's second-granular
     * {@code now()}) or no remote cost (the system clock) should return {@code false}.
     *
     * @return whether the offset-caching fast path may be used
     */
    default boolean useInterval() {
        return true;
    }

    /**
     * Returns the current time from this source, or {@code null} if the source is
     * currently unavailable (the registry will fall back to another source).
     *
     * @return the source's current time, or {@code null} when unavailable
     */
    Date getCurrentDate();

}
