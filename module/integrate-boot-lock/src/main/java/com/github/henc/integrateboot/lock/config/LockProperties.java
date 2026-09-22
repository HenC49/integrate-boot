package com.github.henc.integrateboot.lock.config;

import com.github.henc.integrateboot.lock.LockConst;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the integrate-boot distributed lock layer.
 *
 * <p>Example:
 * <pre>{@code
 * integrate-boot:
 *   lock:
 *     key-prefix: "myapp:lock:"   # default "integrate-boot:lock:" (empty disables namespacing)
 *     default-wait-time: 2s       # default 0s — fail fast instead of blocking
 *     default-lease-time: 30s     # default unset — backend decides (Redis: watchdog)
 * }</pre>
 */
@ConfigurationProperties(prefix = "integrate-boot.lock")
public class LockProperties {

    /**
     * Prefix prepended to every business lock key before it reaches the backend, so lock
     * keys never collide with other data in the shared store. An empty value disables
     * namespacing.
     */
    private String keyPrefix = LockConst.DEFAULT_KEY_PREFIX;

    /**
     * Wait time applied to requests that do not set one. {@link Duration#ZERO} (the
     * default) acquires on a single best-effort attempt instead of blocking.
     */
    private Duration defaultWaitTime = Duration.ZERO;

    /**
     * Lease time applied to requests that do not set one; {@code null} (the default)
     * leaves the lease to the backend — the Redis backend then renews the lock through
     * its watchdog while the holder lives.
     */
    private Duration defaultLeaseTime;

    /**
     * Creates the properties with the platform defaults.
     */
    public LockProperties() {
    }

    /**
     * Returns the prefix prepended to every business lock key.
     *
     * @return the key prefix (never {@code null}; empty disables namespacing)
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Sets the prefix prepended to every business lock key.
     *
     * @param keyPrefix the key prefix, empty to disable namespacing
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * Returns the wait time applied to requests that do not set one.
     *
     * @return the default wait time (never {@code null})
     */
    public Duration getDefaultWaitTime() {
        return defaultWaitTime;
    }

    /**
     * Sets the wait time applied to requests that do not set one.
     *
     * @param defaultWaitTime the default wait time
     */
    public void setDefaultWaitTime(Duration defaultWaitTime) {
        this.defaultWaitTime = defaultWaitTime;
    }

    /**
     * Returns the lease time applied to requests that do not set one.
     *
     * @return the default lease time, or {@code null} to let the backend decide
     */
    public Duration getDefaultLeaseTime() {
        return defaultLeaseTime;
    }

    /**
     * Sets the lease time applied to requests that do not set one.
     *
     * @param defaultLeaseTime the default lease time, or {@code null} for the backend default
     */
    public void setDefaultLeaseTime(Duration defaultLeaseTime) {
        this.defaultLeaseTime = defaultLeaseTime;
    }
}
