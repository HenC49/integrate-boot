package com.github.henc.integrateboot.event.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

/**
 * Keeps Boot's default executor resolution while contributing the module's
 * {@link AsyncUncaughtExceptionHandler}: returning {@code null} from
 * {@link #getAsyncExecutor()} makes {@code @Async} fall back to the
 * {@code applicationTaskExecutor} bean (virtual-thread backed when enabled), so the
 * platform runs one shared async pool instead of a module-private one.
 */
final class EventBusAsyncConfigurer implements AsyncConfigurer {

    private final AsyncUncaughtExceptionHandler exceptionHandler;

    EventBusAsyncConfigurer(AsyncUncaughtExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public Executor getAsyncExecutor() {
        return null;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return exceptionHandler;
    }
}
