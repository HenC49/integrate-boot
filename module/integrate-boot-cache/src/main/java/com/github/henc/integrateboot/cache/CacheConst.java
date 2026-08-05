package com.github.henc.integrateboot.cache;

import java.time.Duration;

/**
 * Shared constants for the integrate-boot local cache layer.
 *
 * <p>These values back the defaults applied by {@code CacheProperties} / {@code CacheAutoConfiguration}
 * and the bean names under which the two {@link org.springframework.cache.CacheManager CacheManagers}
 * are registered.
 */
public final class CacheConst {

    private CacheConst() {
    }

    /**
     * Default time-to-live applied to the expiring cache manager ({@link #MANAGER_EXPIRING}).
     */
    public static final Duration DEFAULT_EXPIRE = Duration.ofMinutes(2);

    /**
     * Default maximum number of entries a cache will hold before eviction kicks in.
     */
    public static final long DEFAULT_MAX_SIZE = 10000L;

    /**
     * Default initial capacity of the underlying Caffeine cache.
     */
    public static final int DEFAULT_INITIAL_CAPACITY = 0;

    /**
     * Bean name of the permanent (no-expiry) cache manager.
     */
    public static final String MANAGER_PERMANENT = "cacheManagerPermanent";

    /**
     * Bean name of the time-to-live cache manager. Marked {@code @Primary}, so it is the
     * default targeted by {@code @Cacheable} when no {@code cacheManager} is named.
     */
    public static final String MANAGER_EXPIRING = "cacheManagerExpiring";
}
