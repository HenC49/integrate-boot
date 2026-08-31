package com.github.henc.integrateboot.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * A business rule was violated — the request itself is well-formed, but the operation it
 * asks for is not allowed in the current state (insufficient balance, duplicate submit,
 * illegal state transition, ...). This is the everyday exception of service code.
 *
 * <p>Defaults: HTTP {@code 200 OK}, business code {@link #CODE} ({@code -1}, matching
 * {@code ResultInfo.CODE_FAILURE}). A business failure is a handled outcome, not a
 * protocol error — the HTTP layer stays 200 and the {@code ResultInfo} envelope carries
 * the failure through {@code success=false} / {@code code} / {@code message}, exactly as
 * {@code ResultInfo.failure(message)} would. Use {@link BadRequestException} (or an
 * explicit status on a {@link BaseException} subclass) when a 4xx is wanted instead:
 *
 * <pre>{@code
 * throw new BusinessException("insufficient balance");
 * // -> HTTP 200, {"success":false,"code":-1,"message":"insufficient balance"}
 *
 * throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK);
 * // -> HTTP 200, {"success":false,"code":10001,"message":"insufficient stock"}
 * }</pre>
 */
public class BusinessException extends BaseException {

    /**
     * Default business code, matching {@code ResultInfo.CODE_FAILURE}.
     */
    public static final int CODE = -1;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a business exception with the default code ({@value #CODE}).
     *
     * @param message failure reason, e.g. {@code "insufficient balance"}
     */
    public BusinessException(String message) {
        super(CODE, message, HttpStatus.OK);
    }

    /**
     * Creates a business exception with an explicit business code.
     *
     * @param code    service-defined error code
     * @param message failure reason
     */
    public BusinessException(int code, String message) {
        super(code, message, HttpStatus.OK);
    }

    /**
     * Creates a business exception carrying the code and message of an {@link ErrorCode}.
     *
     * @param errorCode service-defined error code
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.OK);
    }

    /**
     * Creates a business exception with the default code ({@value #CODE}), preserving the
     * original cause.
     *
     * @param message failure reason
     * @param cause   the underlying exception, kept for logging only
     */
    public BusinessException(String message, Throwable cause) {
        super(CODE, message, HttpStatus.OK, cause);
    }
}
