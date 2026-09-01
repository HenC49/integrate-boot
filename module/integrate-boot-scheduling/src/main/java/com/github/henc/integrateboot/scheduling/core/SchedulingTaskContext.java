package com.github.henc.integrateboot.scheduling.core;

import java.util.Map;

public record SchedulingTaskContext(String taskId, int shardingItem, String shardingParameter,
                                    Map<String, String> parameters) {
}
