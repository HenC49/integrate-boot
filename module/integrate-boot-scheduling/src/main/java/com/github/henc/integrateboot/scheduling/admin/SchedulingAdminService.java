package com.github.henc.integrateboot.scheduling.admin;

import com.github.henc.integrateboot.scheduling.core.SchedulingTaskDefinition;

import java.util.List;

public interface SchedulingAdminService {
    List<SchedulingTaskDefinition> list();
    SchedulingTaskDefinition save(SchedulingTaskDefinition definition);
    void delete(String taskId);
    void pause(String taskId);
    void resume(String taskId);
    void trigger(String taskId);
}
