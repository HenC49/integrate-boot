package com.github.henc.integrateboot.lock;

import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.time.Duration;

/**
 * The lock could not be acquired within the configured wait time — another instance (or
 * thread) is holding it. This is <em>contention</em>, not a failure of the lock layer: it
 * maps to HTTP {@code 409 Conflict} with business code {@link #CODE} ({@value #CODE}), so
 * callers may treat it as a retryable signal while the global exception handler still
 * renders a meaningful response when it escapes an endpoint unhandled.
 *
 * <pre>{@code
 * try {
 *     locks.execute(LockRequest.of("order:" + id), () -> settle(id));
 * } catch (LockNotAcquiredException e) {
 *     // e.getKey() -> "integrate-boot:lock:order:42", e.getWaitTime() -> PT2S
 * }
 * }</pre>
 */
public class LockNotAcquiredException extends LockException {

    /**
     * Default business code, matching the HTTP status value.
     */
    public static final int CODE = 409;

    @Serial
    private static final long serialVersionUID = 1L;

    /** The full backend lock key (prefix included) that could not be acquired. */
    private final String key;

    /** The wait time that elapsed before giving up. */
    private final Duration waitTime;

    /**
     * Creates the exception for one lock key.
     *
     * @param key      the full backend lock key (prefix included)
     * @param waitTime the wait time that elapsed before giving up
     * @param interrupted whether the acquiring thread was interrupted while waiting
     */
    public LockNotAcquiredException(String key, Duration waitTime, boolean interrupted) {
        super(CODE, message(key, waitTime, interrupted), HttpStatus.CONFLICT);
        this.key = key;
        this.waitTime = waitTime;
    }

    /**
     * Returns the full backend lock key (prefix included) that could not be acquired.
     *
     * @return the lock key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the wait time that elapsed before giving up.
     *
     * @return the wait time
     */
    public Duration getWaitTime() {
        return waitTime;
    }

    private static String message(String key, Duration waitTime, boolean interrupted) {
        String reason = interrupted
                ? "interrupted while waiting for lock '" + key + "'"
                : "could not acquire lock '" + key + "' within " + waitTime;
        return reason;
    }
}
