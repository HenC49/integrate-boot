package com.github.henc.integrateboot.exception;

/**
 * A business error code: a stable numeric {@linkplain #getCode() code} plus a human-readable
 * {@linkplain #getMessage() message}, carried by {@link BaseException} into the
 * {@code ResultInfo} failure envelope.
 *
 * <p>This interface is the extension point for modules and services that want their own
 * error-code catalogue — implement it on an enum and pass the constants to the common
 * exceptions (or to a module-defined {@link BaseException} subclass):
 *
 * <pre>{@code
 * public enum OrderErrorCode implements ErrorCode {
 *     INSUFFICIENT_STOCK(10001, "insufficient stock"),
 *     ORDER_NOT_FOUND(10002, "order not found");
 *
 *     private final int code;
 *     private final String message;
 *
 *     OrderErrorCode(int code, String message) {
 *         this.code = code;
 *         this.message = message;
 *     }
 *
 *     @Override
 *     public int getCode() { return code; }
 *
 *     @Override
 *     public String getMessage() { return message; }
 * }
 *
 * throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK);
 * }</pre>
 *
 * <p>Codes are service-defined: {@code ResultInfo} treats any non-zero code as failure, so
 * each module owns its own range (the common exceptions in this module use the 4xx/5xx
 * values matching their HTTP status).
 */
public interface ErrorCode {

    /**
     * The stable numeric error code, rendered as {@code ResultInfo.code}. Any non-zero
     * value means failure.
     *
     * @return the error code
     */
    int getCode();

    /**
     * The human-readable failure reason, rendered as {@code ResultInfo.message}.
     *
     * @return the default message for this error code
     */
    String getMessage();
}
