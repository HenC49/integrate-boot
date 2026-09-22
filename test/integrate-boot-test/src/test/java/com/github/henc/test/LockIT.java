package com.github.henc.test;

import com.github.henc.integrateboot.lock.DistributedLock;
import com.github.henc.integrateboot.lock.LockHandle;
import com.github.henc.integrateboot.lock.LockNotAcquiredException;
import com.github.henc.integrateboot.lock.LockRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end wiring of the distributed lock layer: with the full stack booted (Redis
 * connection included), the {@code integrate-boot-lock} facade is auto-configured over
 * the Redisson-backed provider contributed by the Redis module — mutual exclusion really
 * spans threads, keys land under the configured namespace, and release frees the key.
 */
@SpringBootTest
class LockIT {

    private static final String KEY_PREFIX = "integrate-boot:lock:";

    @Autowired
    private DistributedLock locks;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void executeReturnsTheResultAndFreesTheKeyAfterwards() {
        String result = locks.execute(LockRequest.of("lock-it:execute"), () -> "computed");

        assertThat(result).isEqualTo("computed");
        assertThat(redis.hasKey(KEY_PREFIX + "lock-it:execute")).isFalse();
    }

    @Test
    void contendedLockTimesOutWithTheNotAcquiredException() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> locks.execute(LockRequest.of("lock-it:shared"), () -> {
            held.countDown();
            await(release);
        }), "lock-it-holder");
        holder.start();
        try {
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();

            // While the other thread holds the lock, its Redis key exists under the
            // namespace and a second acquirer fails fast with the contention exception.
            assertThat(redis.hasKey(KEY_PREFIX + "lock-it:shared")).isTrue();
            assertThatThrownBy(() -> locks.execute(LockRequest.builder()
                            .key("lock-it:shared")
                            .waitTime(Duration.ofMillis(200))
                            .build(), () -> "never"))
                    .isInstanceOf(LockNotAcquiredException.class)
                    .hasMessageContaining(KEY_PREFIX + "lock-it:shared")
                    .extracting("code").isEqualTo(409);
        } finally {
            release.countDown();
            holder.join(5000);
        }

        // Released — the next acquirer gets through immediately.
        assertThat(locks.execute(LockRequest.of("lock-it:shared"), () -> "after")).isEqualTo("after");
    }

    @Test
    void manualHandleIsNamespacedAndClosedByTryWithResources() {
        try (LockHandle handle = locks.acquire(LockRequest.of("lock-it:manual"))) {
            assertThat(handle.key()).isEqualTo(KEY_PREFIX + "lock-it:manual");
            assertThat(redis.hasKey(KEY_PREFIX + "lock-it:manual")).isTrue();
        }

        assertThat(redis.hasKey(KEY_PREFIX + "lock-it:manual")).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
