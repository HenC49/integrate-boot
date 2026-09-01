package com.github.henc.integrateboot.scheduling.executor;

import com.github.henc.integrateboot.scheduling.core.SchedulingTaskContext;
import com.github.henc.integrateboot.scheduling.core.SchedulingTaskHandler;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.IJobHandler;

import java.util.Map;

public final class RegisteredSchedulingJobHandler extends IJobHandler {

    private final String taskId;
    private final SchedulingTaskHandler delegate;

    public RegisteredSchedulingJobHandler(String taskId, SchedulingTaskHandler delegate) {
        this.taskId = taskId;
        this.delegate = delegate;
    }

    @Override
    public void execute() throws Exception {
        delegate.execute(new SchedulingTaskContext(taskId, XxlJobHelper.getShardIndex(),
                XxlJobHelper.getJobParam(), Map.of("jobId", Long.toString(XxlJobHelper.getJobId()),
                "logId", Long.toString(XxlJobHelper.getLogId()))));
    }
}
