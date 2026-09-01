package com.github.henc.test;

import com.github.henc.integrateboot.base.job.JobContext;
import com.github.henc.integrateboot.scheduling.executor.SchedulingTaskHandlerRegistry;
import com.github.henc.test.scheduling.service.SampleJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end wiring of {@code @Job} discovery: with scheduling enabled but the XXL-JOB
 * executor left off, the {@code @Job} methods of {@link SampleJobService} — declared
 * against the base module only — are registered in the handler registry and execute
 * through it. This is the "annotate first, enable scheduling later" contract: the same
 * classes compile and boot untouched when scheduling is disabled.
 */
@SpringBootTest(properties = "integrate-boot.scheduling.enabled=true")
class SchedulingIT {

    @Autowired
    private SchedulingTaskHandlerRegistry registry;

    @Autowired
    private SampleJobService sampleJobService;

    @Test
    void jobMethodsAreRegisteredUnderExplicitAndDerivedTaskIds() {
        assertThat(registry.getHandlers())
                .containsKeys("sampleJobWithParam", "cleanup");
    }

    @Test
    void jobMethodsExecuteThroughTheRegistry() throws Exception {
        registry.getHandlers().get("sampleJobWithParam")
                .execute(new JobContext("sampleJobWithParam", 0, null, Map.of("jobParam", "nightly")));
        registry.getHandlers().get("cleanup")
                .execute(new JobContext("cleanup", 0, null, Map.of()));

        assertThat(sampleJobService.getExecuted())
                .containsExactly("sampleJobWithParam:nightly", "cleanup");
    }
}
