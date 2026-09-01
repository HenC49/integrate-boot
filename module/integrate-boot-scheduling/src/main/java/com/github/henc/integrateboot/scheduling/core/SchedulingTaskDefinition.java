package com.github.henc.integrateboot.scheduling.core;

import java.time.ZoneId;
import java.util.Objects;

public record SchedulingTaskDefinition(String taskId, String cron, int shardingTotalCount,
                                       boolean failover, boolean misfire, ZoneId timeZone) {

    public SchedulingTaskDefinition {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(cron, "cron");
        if (taskId.isBlank() || cron.isBlank()) {
            throw new IllegalArgumentException("taskId and cron must not be blank");
        }
        if (shardingTotalCount < 1) {
            throw new IllegalArgumentException("shardingTotalCount must be positive");
        }
        timeZone = timeZone == null ? ZoneId.systemDefault() : timeZone;
    }
}
