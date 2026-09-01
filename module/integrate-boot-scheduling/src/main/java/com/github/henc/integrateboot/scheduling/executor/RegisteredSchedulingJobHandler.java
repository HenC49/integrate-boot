package com.github.henc.integrateboot.scheduling.executor;

import com.github.henc.integrateboot.base.job.JobContext;
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
        // XXL-JOB 3.x exposes no sharding parameter, so shardingParameter stays null here.
        delegate.execute(new JobContext(taskId, XxlJobHelper.getShardIndex(), null,
                Map.of("jobId", Long.toString(XxlJobHelper.getJobId()),
                "logId", Long.toString(XxlJobHelper.getLogId()),
                "jobParam", String.valueOf(XxlJobHelper.getJobParam()))));
    }
}
