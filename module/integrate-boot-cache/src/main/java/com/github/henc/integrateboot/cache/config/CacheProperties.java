package com.github.henc.integrateboot.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the integrate-boot local cache layer.
 *
 * <p>Two {@link org.springframework.cache.CacheManager CacheManagers} are auto-configured, each
 * backed by Caffeine:
 * <ul>
 *   <li>{@code cacheManagerPermanent} — never expires; use for reference data that should live
 *       until evicted by size or cleared explicitly. Spec under {@code permanent.*}.</li>
 *   <li>{@code cacheManagerExpiring} (the {@code @Primary} manager) — entries expire
 *       {@code expire-after-write} after write. Use for request-scoped or volatile data. Spec
 *       under {@code expiring.*}.</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * integrate-boot:
 *   cache:
 *     cache-null-values: true
 *     permanent:
 *       maximum-size: 10000
 *     expiring:
 *       expire-after-write: 2m
 *       maximum-size: 10000
 * }</pre>
 */
@ConfigurationProperties(prefix = "integrate-boot.cache")
public class CacheProperties {

    /**
     * Whether to allow caching of {@code null} values, which helps prevent cache penetration.
     * Applied to both cache managers.
     */
    private boolean cacheNullValues = true;

    /**
     * Spec for the permanent (no-expiry) cache manager.
     */
    private Spec permanent = new Spec();

    /**
     * Spec for the time-to-live cache manager ({@code @Primary}). Defaults to a 2-minute
     * {@code expire-after-write}.
     */
    private Spec expiring = new Spec();

    public CacheProperties() {
        // The expiring manager ships with a sensible default TTL; the permanent manager keeps
        // no expiry by leaving the field null.
        this.expiring.setExpireAfterWrite(CacheConst.DEFAULT_EXPIRE);
    }

    public boolean isCacheNullValues() {
        return cacheNullValues;
    }

    public void setCacheNullValues(boolean cacheNullValues) {
        this.cacheNullValues = cacheNullValues;
    }

    public Spec getPermanent() {
        return permanent;
    }

    public void setPermanent(Spec permanent) {
        this.permanent = permanent;
    }

    public Spec getExpiring() {
        return expiring;
    }

    public void setExpiring(Spec expiring) {
        this.expiring = expiring;
    }

    /**
     * Caffeine cache builder spec for a single cache manager.
     *
     * <p>Every field has a language-level default; only the fields that diverge from Caffeine's
     * defaults need to be set, so unset values (e.g. {@code null} durations) are intentionally
     * skipped when building the {@link com.github.benmanes.caffeine.cache.Caffeine} builder.
     */
    public static class Spec {

        /**
         * Initial capacity of the cache. Defaults to {@link CacheConst#DEFAULT_INITIAL_CAPACITY}.
         */
        private int initialCapacity = CacheConst.DEFAULT_INITIAL_CAPACITY;

        /**
         * Maximum number of entries before the oldest entries are evicted.
         * Defaults to {@link CacheConst#DEFAULT_MAX_SIZE}.
         */
        private long maximumSize = CacheConst.DEFAULT_MAX_SIZE;

        /**
         * Entries are evicted this duration after they were last accessed. {@code null} means
         * access-based expiry is not applied.
         */
        private Duration expireAfterAccess;

        /**
         * Entries are evicted this duration after they were written or updated. {@code null}
         * means write-based expiry is not applied (the default for the permanent manager).
         */
        private Duration expireAfterWrite;

        public int getInitialCapacity() {
            return initialCapacity;
        }

        public void setInitialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Duration getExpireAfterAccess() {
            return expireAfterAccess;
        }

        public void setExpireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
        }

        public Duration getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Duration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }
    }
}
