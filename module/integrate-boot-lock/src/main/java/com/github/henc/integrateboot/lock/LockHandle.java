package com.github.henc.integrateboot.lock;

/**
 * A lock currently held by the caller — the raw handle returned by
 * {@link DistributedLock#acquire(LockRequest)}.
 *
 * <p>Handles are meant for try-with-resources:
 *
 * <pre>{@code
 * try (LockHandle handle = locks.acquire(LockRequest.of("batch:import"))) {
 *     importBatch();
 * }
 * // released here
 * }</pre>
 *
 * <p>Contract for implementations:
 * <ul>
 *   <li>{@link #unlock()} is idempotent — a second call must be a no-op.</li>
 *   <li>{@link #unlock()} must tolerate a lock whose lease has already expired (the
 *       backend released it behind the holder's back); it reports that at most at warn
 *       level instead of throwing.</li>
 *   <li>Handles are not thread-safe and must be released by the thread that acquired
 *       them (backends typically track ownership per thread).</li>
 * </ul>
 */
public interface LockHandle extends AutoCloseable {

    /**
     * Returns the full lock key as handed to the {@link LockProvider} — that is, with the
     * configured key prefix already applied. Mostly informational; the caller knows its
     * own business key.
     *
     * @return the full backend lock key
     */
    String key();

    /**
     * Releases the lock. Idempotent; never throws for an already-expired lock.
     */
    void unlock();

    /**
     * {@link #unlock()} under the try-with-resources name.
     */
    @Override
    default void close() {
        unlock();
    }
}
