package com.github.henc.integrateboot.scheduling.core;

@FunctionalInterface
public interface SchedulingTaskHandler {

    void execute(SchedulingTaskContext context) throws Exception;
}
