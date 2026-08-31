package com.github.henc.integrateboot.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the exception hierarchy contract the global handler relies on: every common
 * exception carries a business code, a message and an HTTP status by default, and both
 * extension points (an {@link ErrorCode} implementation, a {@link BaseException}
 * subclass) preserve them.
 */
class BaseExceptionTest {

    /**
     * A service-defined error-code catalogue, mirroring the documented extension usage.
     */
    enum OrderErrorCode implements ErrorCode {
        INSUFFICIENT_STOCK(10001, "insufficient stock"),
        ORDER_NOT_FOUND(10002, "order not found");

        private final int code;
        private final String message;

        OrderErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public int getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    /**
     * A module-defined exception type, mirroring the documented extension usage.
     */
    static class OrderException extends BaseException {

        OrderException(ErrorCode errorCode) {
            super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.CONFLICT);
        }
    }

    @Test
    void businessExceptionDefaultsToFailureCodeAndOk() {
        BusinessException ex = new BusinessException("insufficient balance");

        assertThat(ex.getCode()).isEqualTo(-1);
        assertThat(ex.getMessage()).isEqualTo("insufficient balance");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void businessExceptionAcceptsExplicitCode() {
        BusinessException ex = new BusinessException(10001, "insufficient stock");

        assertThat(ex.getCode()).isEqualTo(10001);
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void businessExceptionAcceptsErrorCode() {
        BusinessException ex = new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK);

        assertThat(ex.getCode()).isEqualTo(10001);
        assertThat(ex.getMessage()).isEqualTo("insufficient stock");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void businessExceptionPreservesCause() {
        IOException cause = new IOException("db down");
        BusinessException ex = new BusinessException("query failed", cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void commonExceptionsCarryMatchingCodeAndStatus() {
        assertThat(new BadRequestException("bad").getCode()).isEqualTo(400);
        assertThat(new BadRequestException("bad").getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(new UnauthorizedException("no").getCode()).isEqualTo(401);
        assertThat(new UnauthorizedException("no").getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(new ForbiddenException("denied").getCode()).isEqualTo(403);
        assertThat(new ForbiddenException("denied").getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(new NotFoundException("gone").getCode()).isEqualTo(404);
        assertThat(new NotFoundException("gone").getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(new ConflictException("clash").getCode()).isEqualTo(409);
        assertThat(new ConflictException("clash").getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void explicitCodeOverridesDefaultButKeepsStatus() {
        NotFoundException ex = new NotFoundException(10002, "order not found");

        assertThat(ex.getCode()).isEqualTo(10002);
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void errorCodeConstructorKeepsClassStatus() {
        NotFoundException ex = new NotFoundException(OrderErrorCode.ORDER_NOT_FOUND);

        assertThat(ex.getCode()).isEqualTo(10002);
        assertThat(ex.getMessage()).isEqualTo("order not found");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void customSubclassIsABaseException() {
        // The extension contract: any subclass is handled by the catch-all BaseException
        // handler, carrying its own code / message / status.
        OrderException ex = new OrderException(OrderErrorCode.INSUFFICIENT_STOCK);

        assertThat(ex).isInstanceOf(BaseException.class);
        assertThat(ex.getCode()).isEqualTo(10001);
        assertThat(ex.getMessage()).isEqualTo("insufficient stock");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}
