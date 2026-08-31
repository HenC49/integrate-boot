package com.github.henc.integrateboot.redis.datetime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the Redis time source against a stubbed {@link StringRedisTemplate}: the TIME
 * round trip (returning the server time in milliseconds) and the fallback behavior when
 * Redis is unreachable. The live-Redis path is exercised end-to-end by the sample app's
 * integration tests.
 */
class RedisDateTimeServiceTest {

    private final StringRedisTemplate template = mock(StringRedisTemplate.class);

    private final RedisDateTimeService service = new RedisDateTimeService(template);

    @BeforeEach
    void resetStub() {
        when(template.execute(any(RedisCallback.class))).thenThrow(
                new RedisConnectionFailureException("connection refused"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsRedisServerTime() {
        long serverTime = System.currentTimeMillis() - 60_000L;
        when(template.execute(any(RedisCallback.class))).thenReturn(serverTime);

        Date date = service.getCurrentDate();

        assertThat(date).isEqualTo(new Date(serverTime));
    }

    @Test
    void unavailableRedisYieldsNullInsteadOfThrowing() {
        // The @BeforeEach stub makes every execute call fail, like a dead Redis.

        assertThat(service.getCurrentDate()).isNull();
    }

    @Test
    void nullResultYieldsNull() {
        when(template.execute(any(RedisCallback.class))).thenReturn(null);

        assertThat(service.getCurrentDate()).isNull();
    }

    @Test
    void sourceMetadata() {
        assertThat(service.getType()).isEqualTo("redis");
        // Millisecond-precision and cheap — opts into offset caching; priority sits above
        // db (200) but below a custom source (default 0).
        assertThat(service.useInterval()).isTrue();
        assertThat(service.getOrder()).isEqualTo(100);
    }
}
