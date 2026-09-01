package com.github.henc.integrateboot.scheduling.core;

import com.github.henc.integrateboot.base.job.JobContext;

/**
 * Programmatic task model: a Spring bean implementing this interface is registered as a
 * task under its bean name. Prefer the {@link com.github.henc.integrateboot.base.job.Job}
 * annotation on plain bean methods — it needs the base module only.
 */
@FunctionalInterface
public interface SchedulingTaskHandler {

    void execute(JobContext context) throws Exception;
}
