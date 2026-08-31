package com.github.henc.integrateboot.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * The caller is not authenticated — valid credentials must be supplied before the
 * operation may proceed. Maps to HTTP {@code 401 Unauthorized} with business code
 * {@link #CODE} ({@value #CODE}).
 */
public class UnauthorizedException extends BaseException {

    /**
     * Default business code, matching the HTTP status value.
     */
    public static final int CODE = 401;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an unauthorized exception with the default code ({@value #CODE}).
     *
     * @param message failure reason
     */
    public UnauthorizedException(String message) {
        super(CODE, message, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Creates an unauthorized exception with an explicit business code.
     *
     * @param code    service-defined error code
     * @param message failure reason
     */
    public UnauthorizedException(int code, String message) {
        super(code, message, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Creates an unauthorized exception carrying the code and message of an {@link ErrorCode}.
     *
     * @param errorCode service-defined error code
     */
    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.UNAUTHORIZED);
    }
}
