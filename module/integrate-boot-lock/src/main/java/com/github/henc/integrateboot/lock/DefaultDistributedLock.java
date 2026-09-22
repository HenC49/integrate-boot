package com.github.henc.integrateboot.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * The default {@link DistributedLock}: resolves the layer defaults and the key prefix
 * around every request and delegates acquisition to the {@link LockProvider}.
 *
 * <p>Responsibilities kept out of the providers:
 * <ul>
 *   <li><b>Namespacing</b> — the configured prefix is prepended to the business key, so
 *       providers always see full backend keys ({@link LockHandle#key()} reports the same).</li>
 *   <li><b>Defaulting</b> — a request without {@code waitTime}/{@code leaseTime} inherits
 *       the configured defaults; providers receive fully-resolved requests.</li>
 *   <li><b>Error model</b> — an empty provider result becomes
 *       {@link LockNotAcquiredException} (HTTP 409). {@code execute} holds the handle in a
 *       try-with-resources block: release runs even when the action throws, and a release
 *       failure can never mask the action's outcome (it is suppressed beneath it).</li>
 * </ul>
 */
public class DefaultDistributedLock implements DistributedLock {

    private final LockProvider provider;

    private final String keyPrefix;

    private final Duration defaultWaitTime;

    private final Duration defaultLeaseTime;

    /**
     * Creates the facade over one provider.
     *
     * @param provider         the backend SPI to delegate acquisition to
     * @param keyPrefix        prefix prepended to every business key ({@code null} or empty
     *                         disables namespacing)
     * @param defaultWaitTime  wait time for requests that do not set one ({@code null}
     *                         means {@link Duration#ZERO} — fail fast)
     * @param defaultLeaseTime lease time for requests that do not set one ({@code null}
     *                         keeps the lease backend-managed)
     */
    public DefaultDistributedLock(LockProvider provider, String keyPrefix,
                                  Duration defaultWaitTime, Duration defaultLeaseTime) {
        this.provider = provider;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
        this.defaultWaitTime = defaultWaitTime == null ? Duration.ZERO : defaultWaitTime;
        this.defaultLeaseTime = defaultLeaseTime;
    }

    @Override
    public <T> T execute(LockRequest request, Supplier<T> action) {
        // Try-with-resources gives the wanted failure semantics for free: the action's
        // exception wins over any release failure (which ends up suppressed beneath it),
        // and an acquisition failure throws before a handle even exists.
        try (LockHandle handle = acquire(request)) {
            return action.get();
        }
    }

    @Override
    public LockHandle acquire(LockRequest request) {
        LockRequest resolved = resolve(request);
        return provider.tryAcquire(resolved)
                .orElseThrow(() -> new LockNotAcquiredException(
                        resolved.key(), resolved.waitTime(), Thread.currentThread().isInterrupted()));
    }

    /**
     * Applies the key prefix and the configured duration defaults, producing the resolved
     * request the SPI contract promises.
     */
    private LockRequest resolve(LockRequest request) {
        return LockRequest.builder()
                .key(keyPrefix + request.key())
                .waitTime(request.waitTime() != null ? request.waitTime() : defaultWaitTime)
                .leaseTime(request.leaseTime() != null ? request.leaseTime() : defaultLeaseTime)
                .build();
    }
}
