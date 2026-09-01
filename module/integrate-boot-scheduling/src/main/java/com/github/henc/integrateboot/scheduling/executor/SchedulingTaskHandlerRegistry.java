package com.github.henc.integrateboot.scheduling.executor;

import com.github.henc.integrateboot.scheduling.core.SchedulingTaskHandler;
import java.util.Map;

/** Maps explicit Spring bean names to the XXL-JOB handler names exposed to Admin. */
public class SchedulingTaskHandlerRegistry {

    private final Map<String, SchedulingTaskHandler> handlers;

    public SchedulingTaskHandlerRegistry(Map<String, SchedulingTaskHandler> handlers) {
        this.handlers = Map.copyOf(handlers);
    }

    public Map<String, SchedulingTaskHandler> getHandlers() {
        return handlers;
    }
}
