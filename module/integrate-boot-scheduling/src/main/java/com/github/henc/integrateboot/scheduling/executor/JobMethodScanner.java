package com.github.henc.integrateboot.scheduling.executor;

import com.github.henc.integrateboot.base.job.Job;
import com.github.henc.integrateboot.base.job.JobContext;
import com.github.henc.integrateboot.scheduling.core.SchedulingTaskHandler;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Discovers {@link Job @Job} methods across all bean definitions of a bean factory and
 * adapts them to {@link SchedulingTaskHandler}s.
 *
 * <p>The scan works on bean <em>types</em> only ({@code getType}, no instantiation): the
 * returned adapters resolve their target bean lazily at execution time. That keeps the
 * scan safe to run while the context is still being created — also for beans defined
 * later than the registry itself. The annotation must sit on a method of the bean's own
 * class (annotations on interface methods are not inherited by implementations).
 *
 * <p>Task ids: the annotation value when non-blank, otherwise the method name. A
 * duplicate task id — whether from two annotated methods — fails the scan immediately.
 */
public final class JobMethodScanner {

    private JobMethodScanner() {
    }

    /**
     * Scans the given bean factory for {@code @Job} methods.
     *
     * @param beanFactory the factory whose bean definitions are scanned
     * @return handlers keyed by task id, never {@code null}
     * @throws IllegalStateException on an unsupported method signature or a duplicate task id
     */
    public static Map<String, SchedulingTaskHandler> scan(ConfigurableListableBeanFactory beanFactory) {
        Map<String, SchedulingTaskHandler> handlers = new LinkedHashMap<>();
        for (String beanName : beanFactory.getBeanNamesForType(Object.class)) {
            Class<?> beanType = beanFactory.getType(beanName);
            if (beanType == null) {
                continue;
            }
            Map<Method, Job> annotatedMethods = MethodIntrospector.selectMethods(beanType,
                    (MethodIntrospector.MetadataLookup<Job>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method, Job.class));
            for (Map.Entry<Method, Job> entry : annotatedMethods.entrySet()) {
                Method method = entry.getKey();
                String taskId = resolveTaskId(entry.getValue(), method);
                JobMethodHandler handler = new JobMethodHandler(beanFactory, beanName, method,
                        validateSignature(method));
                JobMethodHandler previous = (JobMethodHandler) handlers.put(taskId, handler);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate task id '" + taskId
                            + "': declared by @Job methods " + previous.description()
                            + " and " + handler.description());
                }
            }
        }
        return handlers;
    }

    private static String resolveTaskId(Job job, Method method) {
        String value = job.value();
        return value.isBlank() ? method.getName() : value;
    }

    private static boolean validateSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) {
            return false;
        }
        if (parameterTypes.length == 1 && JobContext.class.isAssignableFrom(parameterTypes[0])) {
            return true;
        }
        throw new IllegalStateException("@Job method " + method.getDeclaringClass().getSimpleName()
                + "#" + method.getName() + " must be declared as either a no-argument method"
                + " or a method taking a single " + JobContext.class.getSimpleName() + " parameter");
    }
}
