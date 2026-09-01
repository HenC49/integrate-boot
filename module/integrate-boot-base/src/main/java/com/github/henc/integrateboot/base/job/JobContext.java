package com.github.henc.integrateboot.base.job;

import java.util.Map;

/**
 * The runtime view of one job execution, handed to {@link Job} methods that declare a
 * {@code JobContext} parameter.
 *
 * <p>Carries the identity of the task plus whatever the scheduling engine knows about
 * the current trigger — most notably the sharding position for broadcast/sharding jobs
 * and the job parameters supplied by the scheduler.
 *
 * @param taskId            the task id the method was registered under
 * @param shardingItem      current shard index (0-based, {@code 0} for non-sharded jobs)
 * @param shardingParameter sharding parameter, may be {@code null} or blank
 * @param parameters        engine-provided execution metadata (e.g. {@code jobId},
 *                          {@code logId}, {@code jobParam}); never {@code null}
 */
public record JobContext(String taskId, int shardingItem, String shardingParameter,
                         Map<String, String> parameters) {

    public JobContext {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
