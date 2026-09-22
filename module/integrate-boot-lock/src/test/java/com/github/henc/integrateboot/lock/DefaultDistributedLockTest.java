package com.github.henc.integrateboot.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Behaviour of {@link DefaultDistributedLock} against an in-memory {@link LockProvider}:
 * namespacing, defaulting, the guaranteed release of {@code execute} and the contention
 * error model. The Redis backend itself is exercised in {@code integrate-boot-redis} and
 * by the sample app's live tests.
 */
class DefaultDistributedLockTest {

    /**
     * Single-slot mutex standing in for a real backend: one held key at a time per set,
     * records every request it was handed.
     */
    static class FakeLockProvider implements LockProvider {

        final Set<String> held = ConcurrentHashMap.newKeySet();

        final List<LockRequest> received = new CopyOnWriteArrayList<>();

        @Override
        public Optional<LockHandle> tryAcquire(LockRequest request) {
            received.add(request);
            if (!held.add(request.key())) {
                return Optional.empty();
            }
            return Optional.of(new LockHandle() {
                @Override
                public String key() {
                    return request.key();
                }

                @Override
                public void unlock() {
                    held.remove(request.key());
                }
            });
        }
    }

    @Test
    void executeReturnsTheActionResultAndReleases() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "", null, null);

        String result = locks.execute(LockRequest.of("order:42"), () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(provider.held).isEmpty();
    }

    @Test
    void runnableExecuteReleasesToo() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "", null, null);

        locks.execute("order:42", () -> {
        });

        assertThat(provider.held).isEmpty();
    }

    @Test
    void actionFailureStillReleasesAndPropagatesTheActionException() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "", null, null);

        assertThatThrownBy(() -> locks.execute(LockRequest.of("order:42"), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(provider.held).isEmpty();
    }

    @Test
    void releaseFailureNeverMasksTheActionOutcome() {
        FakeLockProvider provider = new FakeLockProvider() {
            @Override
            public Optional<LockHandle> tryAcquire(LockRequest request) {
                return Optional.of(new LockHandle() {
                    @Override
                    public String key() {
                        return request.key();
                    }

                    @Override
                    public void unlock() {
                        throw new IllegalStateException("unlock blew up");
                    }
                });
            }
        };
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "", null, null);

        assertThatThrownBy(() -> locks.execute(LockRequest.of("order:42"), () -> {
            throw new IllegalStateException("boom");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom")
                // The provider contract forbids throwing on release; if one misbehaves
                // anyway, try-with-resources must demote it to a suppressed exception.
                .hasSuppressedException(new IllegalStateException("unlock blew up"));
    }

    @Test
    void contentionThrowsNotAcquiredWithTheFullKeyAndResolvedWait() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "p:", Duration.ofSeconds(3), null);
        provider.held.add("p:order:42");

        Throwable thrown = catchThrowable(() ->
                locks.execute(LockRequest.builder().key("order:42").waitTime(Duration.ofSeconds(1)).build(),
                        () -> "never"));

        assertThat(thrown)
                .isInstanceOf(LockNotAcquiredException.class)
                .hasMessageContaining("p:order:42")
                .hasMessageContaining("PT1S");
        assertThat(((LockNotAcquiredException) thrown).getCode()).isEqualTo(409);
        assertThat(((LockNotAcquiredException) thrown).getKey()).isEqualTo("p:order:42");
        assertThat(((LockNotAcquiredException) thrown).getWaitTime()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void interruptedWhileWaitingIsReportedInTheMessage() {
        FakeLockProvider provider = new FakeLockProvider() {
            @Override
            public Optional<LockHandle> tryAcquire(LockRequest request) {
                return Optional.empty();
            }
        };
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "", null, null);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> locks.acquire(LockRequest.of("k")))
                    .isInstanceOf(LockNotAcquiredException.class)
                    .hasMessageContaining("interrupted");
        } finally {
            Thread.interrupted(); // clear the flag for the rest of the test class
        }
    }

    @Test
    void providerFailurePropagatesUnchanged() {
        LockException backendFailure = new LockException("redis down");
        LockProvider broken = request -> {
            throw backendFailure;
        };
        DefaultDistributedLock locks = new DefaultDistributedLock(broken, "", null, null);

        // Infrastructure failures surface as-is: same type, same instance, no wrapping.
        assertThatThrownBy(() -> locks.acquire(LockRequest.of("k")))
                .isSameAs(backendFailure);
    }

    @Test
    void businessKeyIsPrefixedBeforeItReachesTheProvider() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "integrate-boot:lock:", null, null);

        locks.execute("order:42", () -> "ok");

        assertThat(provider.received).hasSize(1);
        assertThat(provider.received.get(0).key()).isEqualTo("integrate-boot:lock:order:42");
    }

    @Test
    void emptyPrefixLeavesTheKeyUntouched() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "", null, null);

        locks.execute("order:42", () -> "ok");

        assertThat(provider.received.get(0).key()).isEqualTo("order:42");
    }

    @Test
    void unsetDurationsInheritTheConfiguredDefaults() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "",
                Duration.ofSeconds(5), Duration.ofSeconds(30));

        locks.execute(LockRequest.of("order:42"), () -> "ok");

        assertThat(provider.received.get(0).waitTime()).isEqualTo(Duration.ofSeconds(5));
        assertThat(provider.received.get(0).leaseTime()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void unsetDurationsFallBackToFailFastAndBackendManagedLease() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "", null, null);

        locks.execute(LockRequest.of("order:42"), () -> "ok");

        assertThat(provider.received.get(0).waitTime()).isEqualTo(Duration.ZERO);
        assertThat(provider.received.get(0).leaseTime()).isNull();
    }

    @Test
    void explicitRequestDurationsWinOverTheDefaults() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "",
                Duration.ofSeconds(5), Duration.ofSeconds(30));

        locks.execute(LockRequest.builder()
                .key("order:42")
                .waitTime(Duration.ofSeconds(1))
                .leaseTime(Duration.ofSeconds(10))
                .build(), () -> "ok");

        assertThat(provider.received.get(0).waitTime()).isEqualTo(Duration.ofSeconds(1));
        assertThat(provider.received.get(0).leaseTime()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void acquireHandsOutTheHandleForManualRelease() {
        FakeLockProvider provider = new FakeLockProvider();
        DefaultDistributedLock locks = new DefaultDistributedLock(provider, "", null, null);

        try (LockHandle handle = locks.acquire(LockRequest.of("order:42"))) {
            assertThat(handle.key()).isEqualTo("order:42");
            assertThat(provider.held).containsExactly("order:42");
        }

        assertThat(provider.held).isEmpty();
    }
}
