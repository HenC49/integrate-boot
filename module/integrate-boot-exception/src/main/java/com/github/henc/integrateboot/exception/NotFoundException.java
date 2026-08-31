package com.github.henc.integrateboot.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * The addressed resource does not exist. Maps to HTTP {@code 404 Not Found} with business
 * code {@link #CODE} ({@value #CODE}).
 *
 * <pre>{@code
 * throw new NotFoundException("order " + id + " not found");
 * // -> HTTP 404, {"success":false,"code":404,"message":"order 1 not found"}
 *
 * throw new NotFoundException(OrderErrorCode.ORDER_NOT_FOUND);
 * // -> HTTP 404, {"success":false,"code":10002,"message":"order not found"}
 * }</pre>
 */
public class NotFoundException extends BaseException {

    /**
     * Default business code, matching the HTTP status value.
     */
    public static final int CODE = 404;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a not-found exception with the default code ({@value #CODE}).
     *
     * @param message failure reason, e.g. {@code "order 1 not found"}
     */
    public NotFoundException(String message) {
        super(CODE, message, HttpStatus.NOT_FOUND);
    }

    /**
     * Creates a not-found exception with an explicit business code.
     *
     * @param code    service-defined error code
     * @param message failure reason
     */
    public NotFoundException(int code, String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }

    /**
     * Creates a not-found exception carrying the code and message of an {@link ErrorCode}.
     *
     * @param errorCode service-defined error code
     */
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.NOT_FOUND);
    }
}
