package com.github.henc.test.scheduling.service;

import com.github.henc.integrateboot.base.job.Job;
import com.github.henc.integrateboot.base.job.JobContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sample job declarations exercising both supported {@code @Job} signatures: an explicit
 * task id with a {@link JobContext} parameter, and a no-argument method defaulting its
 * task id to the method name. The class lives in the main sources on purpose — it
 * compiles against the base module only and stays inert unless the application boots
 * with {@code integrate-boot.scheduling.enabled=true}. Recorded executions let
 * {@code SchedulingIT} observe that the annotated methods actually run.
 */
@Component
public class SampleJobService {

    private final List<String> executed = new CopyOnWriteArrayList<>();

    @Job("sampleJobWithParam")
    public void withContext(JobContext context) {
        executed.add(context.taskId() + ":" + context.parameters().getOrDefault("jobParam", ""));
    }

    @Job
    public void cleanup() {
        executed.add("cleanup");
    }

    public List<String> getExecuted() {
        return executed;
    }
}
