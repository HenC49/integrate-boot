package com.github.henc.integrateboot.scheduling.admin;

import com.github.henc.integrateboot.scheduling.core.SchedulingTaskDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemorySchedulingAdminService implements SchedulingAdminService {
    private final ConcurrentMap<String, SchedulingTaskDefinition> tasks = new ConcurrentHashMap<>();

    @Override public List<SchedulingTaskDefinition> list() { return new ArrayList<>(tasks.values()); }
    @Override public SchedulingTaskDefinition save(SchedulingTaskDefinition definition) {
        tasks.put(definition.taskId(), definition);
        return definition;
    }
    @Override public void delete(String taskId) { tasks.remove(taskId); }
    @Override public void pause(String taskId) { require(taskId); }
    @Override public void resume(String taskId) { require(taskId); }
    @Override public void trigger(String taskId) { require(taskId); }
    private void require(String taskId) {
        if (!tasks.containsKey(taskId)) throw new IllegalArgumentException("Unknown scheduling task: " + taskId);
    }
}
