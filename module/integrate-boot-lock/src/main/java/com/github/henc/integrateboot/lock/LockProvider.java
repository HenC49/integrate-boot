package com.github.henc.integrateboot.lock;

import java.util.Optional;

/**
 * Backend SPI of the distributed lock layer — <em>implemented by a technology module,
 * not by business code</em>. The shipped implementation is the Redis-backed
 * {@code RedissonLockProvider} in {@code integrate-boot-redis}; registering any
 * {@code LockProvider} bean is what activates the {@code DistributedLock} facade (see
 * {@code LockAutoConfiguration}), and a custom provider replaces the default through
 * {@code @ConditionalOnMissingBean}.
 *
 * <p>Contract:
 * <ul>
 *   <li>The request arrives fully resolved: the key carries the configured prefix and
 *       {@link LockRequest#waitTime()} is non-null. {@link LockRequest#leaseTime()} may
 *       still be {@code null}, meaning "use the backend's default lease policy".</li>
 *   <li>Not acquiring within the wait time is an <em>expected</em> outcome — return
 *       {@link Optional#empty()}, never throw. A thread interrupted while waiting also
 *       maps to {@code Optional.empty()} (with the interrupt flag restored).</li>
 *   <li>Infrastructure failures (backend unreachable, ...) throw {@link LockException} so
 *       they render as HTTP 500 through the shared exception hierarchy.</li>
 *   <li>The returned handle must be released only for a lock actually held by the caller
 *       and must follow the {@link LockHandle} contract (idempotent unlock, expired-lease
 *       tolerant).</li>
 * </ul>
 */
public interface LockProvider {

    /**
     * Attempts to acquire the lock described by {@code request}.
     *
     * @param request the resolved lock request (full key, non-null wait time)
     * @return a handle over the held lock, or {@link Optional#empty()} when the lock could
     *         not be acquired within the wait time (including a waiting-thread interrupt)
     * @throws LockException on a backend infrastructure failure
     */
    Optional<LockHandle> tryAcquire(LockRequest request);
}
