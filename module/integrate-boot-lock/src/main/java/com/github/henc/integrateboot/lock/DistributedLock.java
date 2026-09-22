package com.github.henc.integrateboot.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Business-facing facade for distributed locking: run an action while holding a named
 * lock across all instances of the service, or hold the raw {@link LockHandle} yourself.
 *
 * <p>Two shapes cover the common cases:
 *
 * <pre>{@code
 * // 1. execute — acquire, run, guaranteed release (also on failure). Contended locks
 * //    throw LockNotAcquiredException (HTTP 409).
 * String token = locks.execute(LockRequest.builder()
 *         .key("order:" + orderId)
 *         .waitTime(Duration.ofSeconds(2))
 *         .build(), () -> issueToken(orderId));
 *
 * // 2. acquire — the raw handle, for spans execute() cannot express:
 * try (LockHandle handle = locks.acquire(LockRequest.of("batch:import"))) {
 *     importBatch();
 * }
 * }</pre>
 *
 * <p>Keys are business keys ({@code order:42}); the layer prepends the configured prefix
 * (default {@code integrate-boot:lock:}) before a key reaches the backend, so lock keys
 * never collide with other data in the shared store. Wait time and lease time are
 * per-request with layer-wide defaults ({@code integrate-boot.lock.*}): the wait default
 * is fail-fast, the lease default is backend-managed (the Redis backend renews the lock
 * through its watchdog while the holder lives).
 *
 * <p>Backends plug in through the {@link LockProvider} SPI — the auto-configured default
 * is Redis (Redisson) whenever a Redis connection is configured. Mutual exclusion
 * semantics (reentrancy, ownership) are backend-defined; the shipped Redis backend is
 * reentrant per thread and tracks ownership per thread, so handles must be released by
 * the acquiring thread.
 */
public interface DistributedLock {

    /**
     * Runs the action while holding the lock: acquires it, runs the action, and releases
     * the lock afterwards — also when the action throws. When the lock cannot be
     * acquired within the configured wait time, the action never runs and
     * {@link LockNotAcquiredException} is thrown.
     *
     * @param request the lock to acquire (business key required; durations optional)
     * @param action  the action to run under the lock
     * @param <T>     the action's result type
     * @return the action's result
     * @throws LockNotAcquiredException when the lock could not be acquired in time
     * @throws LockException            when the lock backend fails
     */
    <T> T execute(LockRequest request, Supplier<T> action);

    /**
     * {@link #execute(LockRequest, Supplier)} for actions without a result.
     *
     * @param request the lock to acquire
     * @param action  the action to run under the lock
     * @throws LockNotAcquiredException when the lock could not be acquired in time
     * @throws LockException            when the lock backend fails
     */
    default void execute(LockRequest request, Runnable action) {
        execute(request, () -> {
            action.run();
            return null;
        });
    }

    /**
     * {@link #execute(LockRequest, Supplier)} with all layer defaults applied (fail-fast
     * wait, backend-managed lease).
     *
     * @param key    the business lock key
     * @param action the action to run under the lock
     * @param <T>    the action's result type
     * @return the action's result
     * @throws LockNotAcquiredException when the lock could not be acquired
     * @throws LockException            when the lock backend fails
     */
    default <T> T execute(String key, Supplier<T> action) {
        return execute(LockRequest.of(key), action);
    }

    /**
     * {@link #execute(LockRequest, Runnable)} with all layer defaults applied.
     *
     * @param key    the business lock key
     * @param action the action to run under the lock
     * @throws LockNotAcquiredException when the lock could not be acquired
     * @throws LockException            when the lock backend fails
     */
    default void execute(String key, Runnable action) {
        execute(LockRequest.of(key), action);
    }

    /**
     * Acquires the lock and returns its handle; release it through
     * {@link LockHandle#unlock()} — ideally the try-with-resources {@code close()}. The
     * handle must be released by the thread that acquired it.
     *
     * @param request the lock to acquire (business key required; durations optional)
     * @return the handle over the held lock
     * @throws LockNotAcquiredException when the lock could not be acquired in time
     * @throws LockException            when the lock backend fails
     */
    LockHandle acquire(LockRequest request);
}
