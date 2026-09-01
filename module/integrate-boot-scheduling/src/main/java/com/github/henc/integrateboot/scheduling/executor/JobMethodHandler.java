package com.github.henc.integrateboot.scheduling.executor;

import com.github.henc.integrateboot.base.job.JobContext;
import com.github.henc.integrateboot.scheduling.core.SchedulingTaskHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Adapts one {@link com.github.henc.integrateboot.base.job.Job @Job}-annotated method to
 * the {@link SchedulingTaskHandler} SPI. The target bean is resolved from the bean
 * factory lazily on every execution, so discovery never forces premature bean
 * instantiation during context startup.
 */
final class JobMethodHandler implements SchedulingTaskHandler {

    private final BeanFactory beanFactory;
    private final String beanName;
    private final Method method;
    private final boolean contextParameter;

    JobMethodHandler(BeanFactory beanFactory, String beanName, Method method, boolean contextParameter) {
        this.beanFactory = beanFactory;
        this.beanName = beanName;
        this.method = method;
        this.contextParameter = contextParameter;
        ReflectionUtils.makeAccessible(method);
    }

    @Override
    public void execute(JobContext context) throws Exception {
        Object bean = beanFactory.getBean(beanName);
        try {
            if (contextParameter) {
                method.invoke(bean, context);
            } else {
                method.invoke(bean);
            }
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
        }
    }

    private static void rethrowCause(InvocationTargetException ex) throws Exception {
        Throwable cause = ex.getCause();
        if (cause instanceof Exception failure) {
            throw failure;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw ex;
    }

    /**
     * Human-readable origin of this handler for duplicate-id diagnostics.
     */
    String description() {
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }
}
