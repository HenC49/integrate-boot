package com.github.henc.integrateboot.base.job;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobTest {

    @Test
    void annotationTargetsMethodsAndIsRetainedAtRuntime() {
        assertThat(Job.class.getAnnotation(Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Job.class.getAnnotation(java.lang.annotation.Target.class).value())
                .containsExactly(ElementType.METHOD);
    }

    @Test
    void valueDefaultsToEmptyString() throws Exception {
        Method method = Sample.class.getDeclaredMethod("sample");

        assertThat(method.getAnnotation(Job.class).value()).isEmpty();
    }

    @Test
    void jobContextNormalizesParameters() {
        assertThat(new JobContext("taskId", 2, "shard-2", null).parameters()).isEmpty();
        assertThat(new JobContext("taskId", 0, null, Map.of("jobParam", "x")).parameters())
                .containsEntry("jobParam", "x");
    }

    static class Sample {

        @Job
        void sample() {
        }
    }
}
