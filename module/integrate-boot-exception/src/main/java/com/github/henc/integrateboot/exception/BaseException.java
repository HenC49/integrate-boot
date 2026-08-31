package com.github.henc.integrateboot.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Base class of every integrate-boot exception. Carries the three things the global
 * exception handler needs to render a failure response: a business {@linkplain #getCode()
 * code}, a human-readable {@linkplain #getMessage() message} and the {@linkplain
 * #getHttpStatus() HTTP status} the response is sent with.
 *
 * <p>The class is the second extension point of this module (next to {@link ErrorCode}):
 * modules and services define their own exception types by subclassing it, and the global
 * handler picks them up automatically because it catches {@code BaseException}:
 *
 * <pre>{@code
 * public class OrderException extends BaseException {
 *
 *     public OrderException(ErrorCode errorCode) {
 *         super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.CONFLICT);
 *     }
 * }
 *
 * throw new OrderException(OrderErrorCode.INSUFFICIENT_STOCK);
 * // -> HTTP 409, {"success":false,"code":10001,"message":"insufficient stock"}
 * }</pre>
 *
 * <p>For services that do not need a dedicated exception type, the common subclasses
 * ({@link BusinessException}, {@link BadRequestException}, {@link NotFoundException}, ...)
 * accept an {@link ErrorCode} directly, so a code enum alone is enough.
 */
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    private final HttpStatus httpStatus;

    /**
     * Creates an exception from explicit values.
     *
     * @param code       business error code rendered as {@code ResultInfo.code}
     * @param message    failure reason rendered as {@code ResultInfo.message}
     * @param httpStatus HTTP status the global handler responds with
     */
    protected BaseException(int code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * Creates an exception from explicit values, preserving the original cause.
     *
     * @param code       business error code rendered as {@code ResultInfo.code}
     * @param message    failure reason rendered as {@code ResultInfo.message}
     * @param httpStatus HTTP status the global handler responds with
     * @param cause      the underlying exception, kept for logging only
     */
    protected BaseException(int code, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the business error code carried into {@code ResultInfo.code}.
     *
     * @return the error code (any non-zero value means failure)
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the HTTP status the global handler responds with.
     *
     * @return the HTTP status
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
