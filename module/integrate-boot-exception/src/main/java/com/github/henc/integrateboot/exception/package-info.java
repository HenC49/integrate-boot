/**
 * Global exception handling for integrate-boot: a small unchecked exception hierarchy
 * ({@link com.github.henc.integrateboot.exception.BaseException} plus the common
 * subclasses) and one {@code @RestControllerAdvice} that renders every failure into the
 * shared {@code ResultInfo} envelope with a semantically correct HTTP status.
 *
 * <p><b>Extension points</b> — modules and services define their own exceptions through
 * either or both of:
 * <ul>
 *   <li>{@link com.github.henc.integrateboot.exception.ErrorCode} — implement on an enum
 *       to define a module's error-code catalogue, passable to the common exceptions;</li>
 *   <li>{@link com.github.henc.integrateboot.exception.BaseException} — subclass to
 *       define a module's exception types, picked up by the global handler
 *       automatically.</li>
 * </ul>
 *
 * <p>The handler itself is auto-configured by
 * {@code com.github.henc.integrateboot.exception.config.ExceptionAutoConfiguration} and is
 * {@code @ConditionalOnMissingBean}-guarded: an application bean named
 * {@code globalExceptionHandler} replaces it wholesale.
 */
package com.github.henc.integrateboot.exception;
