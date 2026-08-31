package com.github.henc.integrateboot.exception.config;

import com.github.henc.integrateboot.base.ResultInfo;
import com.github.henc.integrateboot.exception.BaseException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler: renders every failure into the shared {@link ResultInfo}
 * envelope with a semantically correct HTTP status, so controllers never hand-roll error
 * responses.
 *
 * <p>Mapping overview:
 * <ul>
 *   <li>{@link BaseException} (and every subclass — common or module-defined) responds
 *       with the code / message / status carried by the exception itself.</li>
 *   <li>Bean Validation and argument-binding failures respond {@code 400} with the
 *       offending fields spelled out.</li>
 *   <li>Wrong HTTP method responds {@code 405}, unsupported media type {@code 415}, and
 *       an unmatched path {@code 404}.</li>
 *   <li>Anything else responds {@code 500} with a generic message — the full stack trace
 *       goes to the log, never to the client.</li>
 * </ul>
 *
 * <p>Logging: expected failures (business exceptions, 4xx) log at {@code WARN} without a
 * stack trace; unexpected ones log at {@code ERROR} with the full trace.
 *
 * <p>Registered as a bean by {@link ExceptionAutoConfiguration} and guarded there with
 * {@code @ConditionalOnMissingBean} — an application that defines its own
 * {@code globalExceptionHandler} bean replaces this one wholesale.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles the integrate-boot exception hierarchy: {@link BaseException} and every
     * subclass, common ({@code BusinessException}, {@code NotFoundException}, ...) or
     * module-defined. Code, message and HTTP status all come from the exception.
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ResultInfo> handleBaseException(BaseException ex) {
        log.warn("{}: {}", ex.getClass().getName(), ex.getMessage());
        return failure(ex.getHttpStatus(), ex.getCode(), ex.getMessage());
    }

    /**
     * Handles a failed {@code @Valid @RequestBody} binding: responds {@code 400} with the
     * offending fields as {@code "field: message"} entries joined by semicolons.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultInfo> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    /**
     * Handles method-level validation failures on controller methods (constraints declared
     * directly on parameters): responds {@code 400} with the violation messages.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ResultInfo> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    /**
     * Handles {@code @Validated} service-layer constraint violations: responds
     * {@code 400} with the violation messages.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResultInfo> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    /**
     * Handles an unreadable request body (malformed JSON, wrong encoding). The parser
     * detail can echo internal class names, so the client gets a stable generic message
     * while the cause is kept for debugging.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResultInfo> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("unreadable request body: {}", ex.getMessage());
        return badRequest("request body is not readable");
    }

    /**
     * Handles a request parameter that cannot be converted to the declared type, e.g.
     * {@code /users/abc} against a {@code Long} path variable.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResultInfo> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("invalid value for parameter '" + ex.getName() + "'");
    }

    /**
     * Handles a missing required request parameter.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResultInfo> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest("missing required parameter '" + ex.getParameterName() + "'");
    }

    /**
     * Handles a request whose HTTP method is not supported by the matched endpoint.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResultInfo> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return failure(HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED.value(),
                "request method '" + ex.getMethod() + "' not supported");
    }

    /**
     * Handles a request whose Content-Type is not accepted by the matched endpoint.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResultInfo> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "media type not supported");
    }

    /**
     * Handles a path no endpoint and no static resource matches.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResultInfo> handleNoResourceFound(NoResourceFoundException ex) {
        return failure(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "resource not found");
    }

    /**
     * Catch-all: an unexpected failure. The client receives a generic message (internal
     * details never leak), the log receives the full stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultInfo> handleUnexpected(Exception ex) {
        log.error("unexpected exception", ex);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "internal server error");
    }

    private static ResponseEntity<ResultInfo> badRequest(String message) {
        return failure(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), message);
    }

    private static ResponseEntity<ResultInfo> failure(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status).body(ResultInfo.failure(code, message));
    }
}
