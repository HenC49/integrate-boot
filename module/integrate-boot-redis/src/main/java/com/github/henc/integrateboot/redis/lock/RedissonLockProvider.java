package com.github.henc.integrateboot.redis.lock;

import com.github.henc.integrateboot.lock.LockHandle;
import com.github.henc.integrateboot.lock.LockProvider;
import com.github.henc.integrateboot.lock.LockRequest;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link LockProvider} SPI implemented on Redisson's reentrant {@link RLock} — the
 * Redis backend of the {@code integrate-boot-lock} facade.
 *
 * <p>Mapping of the request onto Redisson:
 * <ul>
 *   <li>{@code waitTime} bounds how long {@link RLock#tryLock} blocks on a contended
 *       lock ({@code 0} = single best-effort attempt).</li>
 *   <li>An unset {@code leaseTime} uses {@link RLock#tryLock(long, TimeUnit)} — Redisson's
 *       watchdog renews the lock while its holder lives and drops it when the JVM dies,
 *       so a long-running action neither loses the lock nor leaks it.</li>
 *   <li>A set {@code leaseTime} fixes the upper bound via
 *       {@link RLock#tryLock(long, long, TimeUnit)} — for actions with a known maximum
 *       duration where a crashed holder must release quickly.</li>
 * </ul>
 *
 * <p>Ownership: Redisson tracks a lock per thread, so the returned handle must be
 * released by the acquiring thread (exactly what the facade's {@code execute} does) and
 * locking the same key re-entrantly on one thread nests.
 */
public class RedissonLockProvider implements LockProvider {

    private static final Logger log = LoggerFactory.getLogger(RedissonLockProvider.class);

    private final RedissonClient redissonClient;

    /**
     * Creates the provider over one Redisson client (the same client the Redis layer
     * already shares between {@code RedisTemplate} and the connection factory).
     *
     * @param redissonClient the Redisson client to acquire locks with
     */
    public RedissonLockProvider(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Optional<LockHandle> tryAcquire(LockRequest request) {
        RLock rLock = redissonClient.getLock(request.key());
        // The facade resolves every request (waitTime never null there); a raw request
        // handed straight to this provider falls back to the layer's fail-fast default.
        long waitMs = (request.waitTime() != null ? request.waitTime() : Duration.ZERO).toMillis();
        Long leaseMs = request.leaseTime() == null ? null : request.leaseTime().toMillis();
        boolean acquired;
        try {
            if (leaseMs != null) {
                acquired = rLock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
            } else {
                // No lease configured — hand the lock to Redisson's watchdog instead of a
                // fixed expiry, so a slow holder keeps it and a dead holder loses it.
                acquired = rLock.tryLock(waitMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            // Waiting was cut short — restore the flag (callers check it to report the
            // real reason) and report "not acquired", which is what happened.
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        return acquired ? Optional.of(new RedissonLockHandle(request.key(), rLock)) : Optional.empty();
    }

    /**
     * Handle over one held {@link RLock}: idempotent unlock, and an unlock racing an
     * already-expired lease is demoted to a warn log instead of surfacing Redisson's
     * {@code IllegalMonitorStateException}.
     */
    private static final class RedissonLockHandle implements LockHandle {

        private final String key;

        private final RLock lock;

        private final AtomicBoolean released = new AtomicBoolean();

        private RedissonLockHandle(String key, RLock lock) {
            this.key = key;
            this.lock = lock;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public void unlock() {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                // The lease expired (or the holding thread died) before this release ran —
                // the lock is already free, so there is nothing left to do.
                log.warn("lock '{}' was already released when unlock ran (lease expired?)", key);
            }
        }
    }
}
