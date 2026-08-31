package com.github.henc.integrateboot.base.datetime;

import com.github.henc.integrateboot.base.util.DateUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Static registry (and engine) behind {@link DateUtils}
 * that resolves <em>the</em> current time from the registered {@link DateTimeService}
 * sources with fallback.
 *
 * <p><b>Source selection and fallback.</b> The usable source is resolved lazily on the
 * first read and re-resolved whenever the registration set changes: the service matching
 * the configured {@link #setPreferredType preferred type} is tried first; otherwise the
 * registered services are tried by ascending {@link DateTimeService#getOrder() order}.
 * The first source that answers becomes the usable one (selection is sticky — a source
 * that recovers later only takes over after the current one fails). If nothing answers,
 * the system clock is used, so {@code getCurrentDateTime()} never throws and never
 * returns {@code null} even in a plain, non-Spring application with zero registrations.
 *
 * <p><b>Interval mode.</b> When the usable source {@link DateTimeService#useInterval()
 * allows it}, the offset between the source clock and the local clock is recorded once
 * and refreshed by a daemon "housekeeper" thread every {@link #setCheckIntervalMillis
 * check interval} (default 10 minutes); reads then become a local
 * {@code System.currentTimeMillis() + offset} — no remote call per read. Sources that do
 * not allow it are called directly on every read;
 * {@link #getCurrentDateTimeSimpleInterval()} offers a cached middle ground for those.
 *
 * <p><b>Wiring.</b> Applications do not normally touch this class: the
 * {@code integrate-boot-data} and {@code integrate-boot-redis} modules register their
 * sources automatically through their auto-configuration (shared switches live under
 * {@code integrate-boot.datetime.*}). Registration is also possible manually — useful for
 * custom sources or non-Spring applications:
 *
 * <pre>{@code
 * DateTimeRegistry.register(new SatelliteDateTimeService());
 * Date now = DateUtils.getCurrentDateTime();
 * }</pre>
 */
public final class DateTimeRegistry {

    /** Source identifier of the built-in system-clock service: {@value}. */
    public static final String TYPE_SERVER = "server";

    private static final Logger LOGGER = Logger.getLogger(DateTimeRegistry.class.getName());

    /** Built-in last resort — the local system clock; never fails. */
    private static final DateTimeService SYSTEM_SERVICE = new SystemDateTimeService();

    /** Registered sources; copy-on-write keeps registration lock-free for readers. */
    private static final CopyOnWriteArrayList<DateTimeService> SERVICES = new CopyOnWriteArrayList<>();

    private static final Object INIT_LOCK = new Object();

    private static volatile String preferredType;
    private static volatile boolean intervalEnabled = true;
    private static volatile long checkIntervalMillis = Duration.ofMinutes(10).toMillis();

    /** Lazily resolved usable source; {@code null} means (re-)resolution is pending. */
    private static volatile DateTimeService usable;

    private static volatile boolean intervalMode;
    private static volatile long intervalMillis;
    private static volatile boolean housekeeperStarted;

    private static final AtomicLong SIMPLE_INTERVAL = new AtomicLong();
    private static final AtomicLong SIMPLE_NEXT_CHECK = new AtomicLong();
    private static final ReentrantLock SIMPLE_LOCK = new ReentrantLock();

    private static final long HOUSEKEEPER_INITIAL_DELAY_MILLIS = 5_000L;

    private static final ThreadFactory HOUSEKEEPER_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "integrate-boot-datetime-housekeeper");
        thread.setDaemon(true);
        return thread;
    };

    private DateTimeRegistry() {
    }

    // ---------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------

    /**
     * Registers a time source. Idempotent per instance; the usable source is re-resolved
     * so the new source can take over according to preference / priority.
     *
     * @param service the source to register, ignored when {@code null}
     */
    public static void register(DateTimeService service) {
        if (service == null) {
            return;
        }
        if (SERVICES.addIfAbsent(service)) {
            resetResolution();
        }
    }

    /**
     * Unregisters a time source; the usable source is re-resolved if it was this one.
     *
     * @param service the source to unregister, ignored when {@code null} or not registered
     */
    public static void unregister(DateTimeService service) {
        if (service != null && SERVICES.remove(service)) {
            resetResolution();
        }
    }

    /**
     * Snapshot of the currently registered sources, ordered by registration.
     *
     * @return the registered sources, never {@code null}
     */
    public static List<DateTimeService> getRegisteredServices() {
        return List.copyOf(SERVICES);
    }

    // ---------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------

    /**
     * Sets the preferred source {@link DateTimeService#getType() type}, e.g.
     * {@code "redis"}, {@code "db"}, {@code "server"} or a custom type. {@code null}
     * (the default) selects purely by priority order. Setting a type that is not
     * registered or currently unavailable logs a warning and falls back by priority.
     *
     * @param preferredType the preferred source type, or {@code null} for priority order
     */
    public static void setPreferredType(String preferredType) {
        DateTimeRegistry.preferredType =
                (preferredType == null || preferredType.isEmpty()) ? null : preferredType;
        resetResolution();
    }

    /**
     * The configured preferred source type.
     *
     * @return the preferred type, or {@code null} when selection is by priority order
     */
    public static String getPreferredType() {
        return preferredType;
    }

    /**
     * Enables or disables interval (offset-caching) mode, default {@code true}. When
     * disabled, every read goes straight to the usable source.
     *
     * @param intervalEnabled whether offset caching may be used
     */
    public static void setIntervalEnabled(boolean intervalEnabled) {
        DateTimeRegistry.intervalEnabled = intervalEnabled;
        resetResolution();
    }

    /**
     * Whether interval (offset-caching) mode is enabled.
     *
     * @return {@code true} when offset caching may be used
     */
    public static boolean isIntervalEnabled() {
        return intervalEnabled;
    }

    /**
     * How often the housekeeper refreshes the cached clock offset (default 10 minutes)
     * and how often {@link #getCurrentDateTimeSimpleInterval()} re-checks its cached
     * offset. Applied to the housekeeper schedule when it starts.
     *
     * @param millis the refresh interval in milliseconds, positive values only
     */
    public static void setCheckIntervalMillis(long millis) {
        if (millis > 0) {
            checkIntervalMillis = millis;
            // Force the simple-interval cache to re-check with the new interval.
            SIMPLE_NEXT_CHECK.set(0L);
        }
    }

    /**
     * The offset refresh interval.
     *
     * @return the interval in milliseconds
     */
    public static long getCheckIntervalMillis() {
        return checkIntervalMillis;
    }

    // ---------------------------------------------------------------------
    // Current time
    // ---------------------------------------------------------------------

    /**
     * The current time. Uses the cached-offset fast path when the usable source allows
     * it; otherwise calls the source directly on every read. Never returns {@code null}
     * and never throws — worst case it answers from the system clock.
     *
     * @return the current time from the best available source
     */
    public static Date getCurrentDateTime() {
        if (intervalMode) {
            return new Date(System.currentTimeMillis() + intervalMillis);
        }
        return getDateFromService();
    }

    /**
     * A cheaper variant for high-frequency callers: like {@link #getCurrentDateTime()},
     * but when the usable source does not allow offset caching it still avoids a remote
     * call per read — the source is consulted at most once per check interval and the
     * local clock interpolates in between (millisecond precision between checks, exact
     * precision on each re-check).
     *
     * @return the current time, interpolated from a cached offset where possible
     */
    public static Date getCurrentDateTimeSimpleInterval() {
        long now = System.currentTimeMillis();
        if (intervalMode) {
            return new Date(now + intervalMillis);
        }
        if (now >= SIMPLE_NEXT_CHECK.get() && SIMPLE_LOCK.tryLock()) {
            try {
                Date actual = getDateFromService();
                SIMPLE_INTERVAL.set(actual.getTime() - now);
                SIMPLE_NEXT_CHECK.set(now + checkIntervalMillis);
            } finally {
                SIMPLE_LOCK.unlock();
            }
        }
        return new Date(now + SIMPLE_INTERVAL.get());
    }

    /**
     * The current time read <em>directly</em> from the usable source, bypassing any
     * cached offset. Falls back like every other read: a source that just failed is
     * re-resolved, and the system clock answers if nothing is available.
     *
     * @return the current time read directly from the usable source
     */
    public static Date getDateFromService() {
        Date date = probe(usableService());
        if (date == null) {
            // The resolved source just failed — re-resolve, possibly onto another source.
            date = probe(resolveUsableService());
        }
        return date != null ? date : new Date();
    }

    /**
     * The currently usable source, resolving (and starting the housekeeper) on first
     * use or after a reset.
     */
    static DateTimeService usableService() {
        DateTimeService service = usable;
        if (service == null) {
            synchronized (INIT_LOCK) {
                service = resolveUsableService();
            }
        }
        return service;
    }

    /** Must hold {@link #INIT_LOCK}; resolves the usable source if resolution is pending. */
    private static DateTimeService resolveUsableService() {
        DateTimeService service = usable;
        if (service == null) {
            service = pick();
            usable = service;
            applyIntervalMode(service);
            if (intervalMode && !housekeeperStarted) {
                startHousekeeper();
            }
        }
        return service;
    }

    // ---------------------------------------------------------------------
    // Resolution internals
    // ---------------------------------------------------------------------

    /** Picks a working source: preferred type first, then priority order, then system clock. */
    private static DateTimeService pick() {
        List<DateTimeService> candidates = new ArrayList<>(SERVICES);
        candidates.sort(Comparator.comparingInt(DateTimeService::getOrder));

        String prefer = preferredType;
        if (prefer != null) {
            if (TYPE_SERVER.equals(prefer)) {
                return SYSTEM_SERVICE;
            }
            DateTimeService preferred = candidates.stream()
                    .filter(service -> prefer.equals(service.getType()))
                    .findFirst()
                    .orElse(null);
            if (preferred != null) {
                if (probe(preferred) != null) {
                    return preferred;
                }
                LOGGER.log(Level.WARNING,
                        "preferred datetime source ''{0}'' is unavailable, falling back by priority", prefer);
            } else {
                LOGGER.log(Level.WARNING,
                        "preferred datetime source ''{0}'' is not registered, falling back by priority", prefer);
            }
        }
        for (DateTimeService candidate : candidates) {
            if (probe(candidate) != null) {
                return candidate;
            }
        }
        if (candidates.isEmpty()) {
            LOGGER.info("no DateTimeService registered, using the system clock");
        } else {
            LOGGER.warning("no registered DateTimeService is available, using the system clock");
        }
        return SYSTEM_SERVICE;
    }

    /** Must hold {@link #INIT_LOCK}; computes interval mode/offset for the given source. */
    private static void applyIntervalMode(DateTimeService service) {
        intervalMode = false;
        if (intervalEnabled && service.useInterval()) {
            Date remote = probe(service);
            if (remote != null) {
                intervalMillis = remote.getTime() - System.currentTimeMillis();
                intervalMode = true;
            }
        }
    }

    /**
     * One source read for availability probing: {@code null} on failure instead of an
     * exception, so a broken source is a fallback signal rather than an outage.
     */
    private static Date probe(DateTimeService service) {
        try {
            return service.getCurrentDate();
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "datetime source ''{0}'' failed: {1}",
                    new Object[] {service.getType(), e});
            return null;
        }
    }

    /** Marks resolution as pending; the next read re-resolves and re-computes offsets. */
    private static void resetResolution() {
        usable = null;
        intervalMode = false;
        SIMPLE_NEXT_CHECK.set(0L);
    }

    // ---------------------------------------------------------------------
    // Housekeeper
    // ---------------------------------------------------------------------

    private static void startHousekeeper() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1, HOUSEKEEPER_FACTORY, new ThreadPoolExecutor.DiscardPolicy());
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);
        executor.scheduleWithFixedDelay(DateTimeRegistry::housekeeping,
                HOUSEKEEPER_INITIAL_DELAY_MILLIS, checkIntervalMillis, TimeUnit.MILLISECONDS);
        housekeeperStarted = true;
        LOGGER.log(Level.INFO, "datetime housekeeper started, refresh interval {0} ms", checkIntervalMillis);
    }

    /**
     * Refreshes the cached offset from the usable source, and re-resolves when that
     * source stopped answering. Probing of replacement candidates happens outside
     * {@link #INIT_LOCK} so a slow probe never blocks concurrent time reads.
     */
    private static void housekeeping() {
        DateTimeService current = usable;
        if (current == null) {
            // Not initialized or a reset is pending — the next read re-initializes.
            return;
        }
        Date remote = probe(current);
        if (remote != null) {
            if (intervalMode) {
                intervalMillis = remote.getTime() - System.currentTimeMillis();
            }
            return;
        }
        LOGGER.log(Level.WARNING, "datetime source ''{0}'' became unavailable, re-resolving", current.getType());
        DateTimeService next = pick();
        synchronized (INIT_LOCK) {
            usable = next;
            applyIntervalMode(next);
        }
    }

    /** Test hook: clears registrations, configuration and cached state. */
    static void reset() {
        SERVICES.clear();
        preferredType = null;
        intervalEnabled = true;
        checkIntervalMillis = Duration.ofMinutes(10).toMillis();
        resetResolution();
        SIMPLE_INTERVAL.set(0L);
    }

}
