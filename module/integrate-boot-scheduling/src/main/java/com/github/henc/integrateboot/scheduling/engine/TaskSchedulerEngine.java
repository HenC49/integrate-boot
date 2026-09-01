package com.github.henc.integrateboot.scheduling.engine;

import com.github.henc.integrateboot.scheduling.core.SchedulingTaskDefinition;

public interface TaskSchedulerEngine {

    void register(SchedulingTaskDefinition definition);

    void update(SchedulingTaskDefinition definition);

    void remove(String taskId);

    void pause(String taskId);

    void resume(String taskId);

    void trigger(String taskId);
}
