package com.github.henc.integrateboot.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * The request is malformed — a structurally invalid payload the endpoint cannot process.
 * Distinct from {@link BusinessException}: {@code BadRequest} means the request itself is
 * wrong, not that a business rule rejected it.
 *
 * <p>Defaults to HTTP {@code 400 Bad Request} with business code {@link #CODE}
 * ({@value #CODE}).
 */
public class BadRequestException extends BaseException {

    /**
     * Default business code, matching the HTTP status value.
     */
    public static final int CODE = 400;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a bad-request exception with the default code ({@value #CODE}).
     *
     * @param message failure reason
     */
    public BadRequestException(String message) {
        super(CODE, message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception with an explicit business code.
     *
     * @param code    service-defined error code
     * @param message failure reason
     */
    public BadRequestException(int code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception carrying the code and message of an {@link ErrorCode}.
     *
     * @param errorCode service-defined error code
     */
    public BadRequestException(ErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.BAD_REQUEST);
    }
}
