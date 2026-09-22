package com.github.henc.integrateboot.lock;

import com.github.henc.integrateboot.exception.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * A distributed-lock layer failure: the backend could not be reached or answered
 * unexpectedly (connection down, backend error while acquiring or releasing). Renders as
 * HTTP {@code 500} with business code {@link #CODE} ({@value #CODE}).
 *
 * <p>Root of the module's exception hierarchy — catching {@link LockException} also
 * catches {@link LockNotAcquiredException}.
 */
public class LockException extends BaseException {

    /**
     * Default business code, matching the HTTP status value.
     */
    public static final int CODE = 500;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a lock layer failure with the default code ({@value #CODE}).
     *
     * @param message failure reason
     */
    public LockException(String message) {
        super(CODE, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Creates a lock layer failure with the default code ({@value #CODE}), preserving the
     * underlying cause.
     *
     * @param message failure reason
     * @param cause   the underlying backend failure, kept for logging
     */
    public LockException(String message, Throwable cause) {
        super(CODE, message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    /**
     * Creates a lock layer failure with an explicit status/code — how module-defined
     * subclasses select their own HTTP semantics.
     *
     * @param code       business error code rendered as {@code ResultInfo.code}
     * @param message    failure reason
     * @param httpStatus HTTP status the global handler responds with
     */
    protected LockException(int code, String message, HttpStatus httpStatus) {
        super(code, message, httpStatus);
    }
}
