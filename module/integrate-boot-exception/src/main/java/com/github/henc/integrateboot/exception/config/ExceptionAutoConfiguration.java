package com.github.henc.integrateboot.exception.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception-handling auto-configuration for integrate-boot.
 *
 * <p>Registers the {@link GlobalExceptionHandler} advice in servlet web applications, so
 * every failure — common or module-defined — is rendered into the shared
 * {@code ResultInfo} envelope with zero configuration. The bean is
 * {@code @ConditionalOnMissingBean}-guarded: an application that defines its own
 * {@code globalExceptionHandler} bean replaces the default wholesale.
 */
@AutoConfiguration
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ExceptionAutoConfiguration {

    /**
     * The global {@code @RestControllerAdvice} turning exceptions into
     * {@code ResultInfo} failure envelopes.
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
