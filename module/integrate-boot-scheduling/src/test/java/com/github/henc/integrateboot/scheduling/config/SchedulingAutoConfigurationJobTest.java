package com.github.henc.integrateboot.scheduling.config;

import com.github.henc.integrateboot.base.job.Job;
import com.github.henc.integrateboot.base.job.JobContext;
import com.github.henc.integrateboot.scheduling.core.SchedulingTaskHandler;
import com.github.henc.integrateboot.scheduling.executor.SchedulingTaskHandlerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration level wiring of the {@code @Job} discovery: with scheduling
 * enabled, the registry merges interface-based handlers and annotated methods into one
 * namespace, and a task id claimed by both models fails startup.
 */
class SchedulingAutoConfigurationJobTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SchedulingAutoConfiguration.class));

    @Test
    void registryMergesInterfaceBeansAndJobMethods() {
        runner.withPropertyValues("integrate-boot.scheduling.enabled=true")
                .withUserConfiguration(MixedJobsConfig.class)
                .run(context -> assertThat(
                        context.getBean(SchedulingTaskHandlerRegistry.class).getHandlers())
                        .containsKeys("explicitTaskId", "noArg", "interfaceHandler"));
    }

    @Test
    void jobMethodsStayDormantWhenSchedulingIsDisabled() {
        runner.withUserConfiguration(MixedJobsConfig.class)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SchedulingTaskHandlerRegistry.class));
    }

    @Test
    void taskIdClaimedByBothModelsFailsStartup() {
        runner.withPropertyValues("integrate-boot.scheduling.enabled=true")
                .withUserConfiguration(CollidingJobsConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Duplicate task id 'noArg'");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MixedJobsConfig {

        @Bean
        SampleJobs sampleJobs() {
            return new SampleJobs();
        }

        @Bean
        SchedulingTaskHandler interfaceHandler() {
            return context -> {
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CollidingJobsConfig {

        // Bean name "noArg" collides with the @Job method id "noArg" of SampleJobs.
        @Bean
        SchedulingTaskHandler noArg() {
            return context -> {
            };
        }

        @Bean
        SampleJobs sampleJobs() {
            return new SampleJobs();
        }
    }

    static class SampleJobs {

        @Job("explicitTaskId")
        public void withContext(JobContext context) {
        }

        @Job
        public void noArg() {
        }
    }
}
