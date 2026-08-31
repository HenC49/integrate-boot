package com.github.henc.integrateboot.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * The request conflicts with the current state of the target resource — a duplicate key,
 * an already-processed idempotency token, a concurrent modification. Maps to HTTP
 * {@code 409 Conflict} with business code {@link #CODE} ({@value #CODE}).
 */
public class ConflictException extends BaseException {

    /**
     * Default business code, matching the HTTP status value.
     */
    public static final int CODE = 409;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a conflict exception with the default code ({@value #CODE}).
     *
     * @param message failure reason
     */
    public ConflictException(String message) {
        super(CODE, message, HttpStatus.CONFLICT);
    }

    /**
     * Creates a conflict exception with an explicit business code.
     *
     * @param code    service-defined error code
     * @param message failure reason
     */
    public ConflictException(int code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }

    /**
     * Creates a conflict exception carrying the code and message of an {@link ErrorCode}.
     *
     * @param errorCode service-defined error code
     */
    public ConflictException(ErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.CONFLICT);
    }
}
