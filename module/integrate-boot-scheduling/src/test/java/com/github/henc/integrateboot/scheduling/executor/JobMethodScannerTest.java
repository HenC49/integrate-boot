package com.github.henc.integrateboot.scheduling.executor;

import com.github.henc.integrateboot.base.job.Job;
import com.github.henc.integrateboot.base.job.JobContext;
import com.github.henc.integrateboot.scheduling.core.SchedulingTaskHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Discovery of {@code @Job} methods over a real (but minimal) application context: the
 * scan must find beans registered as plain classes, derive task ids, reject unsupported
 * signatures and duplicate ids, and execute through the reflective adapter — including
 * unwrapping business exceptions.
 */
class JobMethodScannerTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void scanDiscoversExplicitAndDerivedTaskIds() {
        register(SampleJobBean.class);

        assertThat(JobMethodScanner.scan(context.getBeanFactory()))
                .containsKeys("explicitTaskId", "noArg");
    }

    @Test
    void adaptersExecuteTheAnnotatedMethods() throws Exception {
        register(SampleJobBean.class);
        SampleJobBean bean = context.getBean(SampleJobBean.class);
        Map<String, SchedulingTaskHandler> handlers = JobMethodScanner.scan(context.getBeanFactory());

        JobContext jobContext = new JobContext("explicitTaskId", 1, null, Map.of("jobParam", "nightly"));
        handlers.get("explicitTaskId").execute(jobContext);
        handlers.get("noArg").execute(new JobContext("noArg", 0, null, Map.of()));

        assertThat(bean.executed).containsExactly("withContext", "noArg");
        assertThat(bean.lastContext).isSameAs(jobContext);
    }

    @Test
    void businessExceptionsAreUnwrapped() {
        register(FailingJobBean.class);
        SchedulingTaskHandler handler = JobMethodScanner.scan(context.getBeanFactory()).get("boom");

        assertThatThrownBy(() -> handler.execute(new JobContext("boom", 0, null, Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("business failure");
    }

    @Test
    void unsupportedSignatureFailsFast() {
        register(BrokenSignatureJob.class);

        assertThatThrownBy(() -> JobMethodScanner.scan(context.getBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid")
                .hasMessageContaining("JobContext");
    }

    @Test
    void duplicateTaskIdFailsFast() {
        register(DuplicateJobBeanA.class, DuplicateJobBeanB.class);

        assertThatThrownBy(() -> JobMethodScanner.scan(context.getBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate task id 'sameId'")
                .hasMessageContaining("sameId")
                .hasMessageContaining("other");
    }

    private void register(Class<?>... annotatedClasses) {
        context.register(annotatedClasses);
        context.refresh();
    }

    static class SampleJobBean {

        final List<String> executed = new CopyOnWriteArrayList<>();
        volatile JobContext lastContext;

        @Job("explicitTaskId")
        public void withContext(JobContext context) {
            executed.add("withContext");
            this.lastContext = context;
        }

        @Job
        public void noArg() {
            executed.add("noArg");
        }
    }

    static class FailingJobBean {

        @Job
        public void boom() {
            throw new IllegalStateException("business failure");
        }
    }

    static class BrokenSignatureJob {

        @Job
        public void invalid(String parameter) {
        }
    }

    static class DuplicateJobBeanA {

        @Job
        public void sameId() {
        }
    }

    static class DuplicateJobBeanB {

        @Job("sameId")
        public void other() {
        }
    }
}
