package com.github.henc.integrateboot.redis.lock;

import com.github.henc.integrateboot.lock.LockHandle;
import com.github.henc.integrateboot.lock.LockRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mapping of the lock SPI onto Redisson's {@link RLock}, against a stubbed client: the
 * watchdog-vs-fixed-lease dispatch, the wait time, the interrupted and the not-acquired
 * paths, and the expired-lease-tolerant release. The live-Redis path is exercised
 * end-to-end by the sample app's integration tests.
 */
class RedissonLockProviderTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);

    private final RLock rLock = mock(RLock.class);

    private final RedissonLockProvider provider = new RedissonLockProvider(redissonClient);

    @BeforeEach
    void stubLockCreation() {
        // The provider is under test directly (the facade that applies the key prefix is
        // exercised in integrate-boot-lock), so the keys here are the raw, unprefixed ones.
        when(redissonClient.getLock("order:42")).thenReturn(rLock);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void unsetLeaseUsesTheWatchdogTryLock() throws Exception {
        when(rLock.tryLock(0L, TimeUnit.MILLISECONDS)).thenReturn(true);

        Optional<LockHandle> handle = provider.tryAcquire(LockRequest.of("order:42"));

        assertThat(handle).isPresent();
        assertThat(handle.get().key()).isEqualTo("order:42");
        verify(rLock, never()).tryLock(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fixedLeaseIsForwardedToRedisson() throws Exception {
        when(rLock.tryLock(1000L, 30000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        Optional<LockHandle> handle = provider.tryAcquire(LockRequest.builder()
                .key("order:42")
                .waitTime(Duration.ofSeconds(1))
                .leaseTime(Duration.ofSeconds(30))
                .build());

        assertThat(handle).isPresent();
        verify(rLock).tryLock(1000L, 30000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void timeoutYieldsEmptyInsteadOfThrowing() throws Exception {
        when(rLock.tryLock(200L, TimeUnit.MILLISECONDS)).thenReturn(false);

        Optional<LockHandle> handle = provider.tryAcquire(LockRequest.builder()
                .key("order:42")
                .waitTime(Duration.ofMillis(200))
                .build());

        assertThat(handle).isEmpty();
    }

    @Test
    void interruptedWaitingMapsToEmptyAndRestoresTheFlag() throws Exception {
        when(rLock.tryLock(0L, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());

        Optional<LockHandle> handle = provider.tryAcquire(LockRequest.of("order:42"));

        assertThat(handle).isEmpty();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void unlockReleasesOnceAndIsIdempotent() throws Exception {
        when(rLock.tryLock(0L, TimeUnit.MILLISECONDS)).thenReturn(true);
        LockHandle handle = provider.tryAcquire(LockRequest.of("order:42")).orElseThrow();

        handle.unlock();
        handle.unlock();

        verify(rLock).unlock();
    }

    @Test
    void unlockToleratesAnAlreadyExpiredLease() throws Exception {
        when(rLock.tryLock(0L, TimeUnit.MILLISECONDS)).thenReturn(true);
        LockHandle handle = provider.tryAcquire(LockRequest.of("order:42")).orElseThrow();
        org.mockito.Mockito.doThrow(new IllegalMonitorStateException("lock already expired"))
                .when(rLock).unlock();

        // The lease expired behind the holder's back — the release reports at most at
        // warn level and must not bubble up into business code.
        assertThatNoException().isThrownBy(handle::unlock);
    }
}
