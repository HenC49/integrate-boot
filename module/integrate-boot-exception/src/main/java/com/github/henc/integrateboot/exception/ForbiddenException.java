package com.github.henc.integrateboot.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * The caller is authenticated but lacks the authority for this operation. Maps to HTTP
 * {@code 403 Forbidden} with business code {@link #CODE} ({@value #CODE}).
 */
public class ForbiddenException extends BaseException {

    /**
     * Default business code, matching the HTTP status value.
     */
    public static final int CODE = 403;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a forbidden exception with the default code ({@value #CODE}).
     *
     * @param message failure reason
     */
    public ForbiddenException(String message) {
        super(CODE, message, HttpStatus.FORBIDDEN);
    }

    /**
     * Creates a forbidden exception with an explicit business code.
     *
     * @param code    service-defined error code
     * @param message failure reason
     */
    public ForbiddenException(int code, String message) {
        super(code, message, HttpStatus.FORBIDDEN);
    }

    /**
     * Creates a forbidden exception carrying the code and message of an {@link ErrorCode}.
     *
     * @param errorCode service-defined error code
     */
    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.FORBIDDEN);
    }
}
