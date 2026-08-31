package com.github.henc.integrateboot.redis.datetime;

import com.github.henc.integrateboot.base.datetime.DateTimeService;
import com.github.henc.integrateboot.base.util.DateUtils;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * The {@code "redis"} time source for {@link DateUtils}:
 * the Redis server's clock, read with the {@code TIME} command. Redis keeps millisecond
 * (internally microsecond) precision and the command is cheap, so the source opts into
 * offset caching — reads through {@code DateUtils} normally come from a locally cached
 * offset that a background thread refreshes, instead of a Redis round trip per call.
 *
 * <p>Registered automatically by {@code integrate-boot-redis} when a Redis connection is
 * configured. If Redis is unreachable, {@link #getCurrentDate()} returns {@code null} —
 * a fallback signal, never an outage: {@link com.github.henc.integrateboot.base.datetime.DateTimeRegistry}
 * then answers from another source or the system clock.
 */
public class RedisDateTimeService implements DateTimeService {

    /** Source identifier: {@value}. */
    public static final String TYPE = "redis";

    /** Priority: above {@code db} (200); a custom source (default 0) still outranks it. */
    public static final int ORDER = 100;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisDateTimeService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public boolean useInterval() {
        return true;
    }

    @Override
    public Date getCurrentDate() {
        try {
            Long time = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                    connection.serverCommands().time(TimeUnit.MILLISECONDS));
            return time == null ? null : new Date(time);
        } catch (RuntimeException e) {
            // Redis unreachable / command failed — let the registry fall back.
            return null;
        }
    }

}
