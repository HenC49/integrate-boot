package com.github.henc.integrateboot.lock;

import java.time.Duration;

/**
 * Description of the lock to acquire, handed to
 * {@link DistributedLock#execute(LockRequest, java.util.function.Supplier)} /
 * {@link DistributedLock#acquire(LockRequest)} and forwarded to the
 * {@link LockProvider}.
 *
 * <p>Only the {@linkplain Builder#key(String) key} is required. The two durations are
 * optional and defaulted by the layer (see {@code LockProperties}):
 *
 * <ul>
 *   <li>{@link Builder#waitTime(Duration)} — how long to wait for a contended lock before
 *       giving up; the default is {@link Duration#ZERO} (fail fast rather than block).</li>
 *   <li>{@link Builder#leaseTime(Duration)} — how long the lock stays held even if the
 *       holder never releases; the default leaves the choice to the backend. The Redis
 *       backend treats an unset lease as "watchdog-managed": the lock is renewed while the
 *       holder lives, so a long-running action does not lose it.</li>
 * </ul>
 *
 * <p>Instances are immutable. Typical usage:
 *
 * <pre>{@code
 * DistributedLock locks = ...;
 *
 * // run under the lock, waiting up to 2s for a concurrent holder:
 * locks.execute(LockRequest.builder().key("order:42").waitTime(Duration.ofSeconds(2)).build(),
 *         () -> settle(orderId));
 *
 * // one-liner with all defaults (try once, backend-managed lease):
 * locks.execute(LockRequest.of("report:monthly"), () -> render());
 * }</pre>
 */
public final class LockRequest {

    private final String key;
    private final Duration waitTime;
    private final Duration leaseTime;

    private LockRequest(Builder builder) {
        this.key = builder.key;
        this.waitTime = builder.waitTime;
        this.leaseTime = builder.leaseTime;
    }

    /**
     * Creates a minimal request carrying just the key.
     *
     * @param key business lock key
     * @return the request
     */
    public static LockRequest of(String key) {
        return builder().key(key).build();
    }

    /**
     * Returns a fresh builder.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the business lock key, e.g. {@code order:42}; never {@code null}. The
     * configured key prefix is applied by the {@code DistributedLock} layer, not carried
     * here — callers always use business keys.
     *
     * @return the lock key
     */
    public String key() {
        return key;
    }

    /**
     * Returns the caller-specified wait time, or {@code null} to use the configured
     * default (fail fast when left at the default).
     *
     * @return the wait time or {@code null}
     */
    public Duration waitTime() {
        return waitTime;
    }

    /**
     * Returns the caller-specified lease time, or {@code null} to use the configured
     * default (unset by default, which leaves the lease to the backend).
     *
     * @return the lease time or {@code null}
     */
    public Duration leaseTime() {
        return leaseTime;
    }

    /**
     * Builder of {@link LockRequest}; call {@link #build()} once every field is set.
     */
    public static final class Builder {

        private String key;
        private Duration waitTime;
        private Duration leaseTime;

        private Builder() {
        }

        /**
         * Sets the business lock key (required, non-blank).
         *
         * @param key lock key, e.g. {@code order:42}
         * @return this builder
         */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * Sets how long to wait for a contended lock before giving up. Zero (the layer
         * default) acquires on a best-effort single attempt. Negative values are rejected.
         *
         * @param waitTime time to wait, {@code null} to use the configured default
         * @return this builder
         */
        public Builder waitTime(Duration waitTime) {
            this.waitTime = waitTime;
            return this;
        }

        /**
         * Sets how long the lock may stay held without renewal — an upper bound for the
         * guarded action. An unset lease keeps the backend default (the Redis backend's
         * watchdog renews the lock while its holder lives). Negative values are rejected.
         *
         * @param leaseTime lease duration, {@code null} to use the configured default
         * @return this builder
         */
        public Builder leaseTime(Duration leaseTime) {
            this.leaseTime = leaseTime;
            return this;
        }

        /**
         * Builds the request; fails when the key is missing or blank or a duration is
         * negative.
         *
         * @return the immutable request
         */
        public LockRequest build() {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("LockRequest requires a non-blank key");
            }
            if (waitTime != null && waitTime.isNegative()) {
                throw new IllegalArgumentException("LockRequest waitTime must not be negative");
            }
            if (leaseTime != null && leaseTime.isNegative()) {
                throw new IllegalArgumentException("LockRequest leaseTime must not be negative");
            }
            return new LockRequest(this);
        }
    }
}
