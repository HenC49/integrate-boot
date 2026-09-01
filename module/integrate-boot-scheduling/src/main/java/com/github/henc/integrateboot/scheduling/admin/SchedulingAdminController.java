package com.github.henc.integrateboot.scheduling.admin;

import com.github.henc.integrateboot.base.ResultInfo;
import com.github.henc.integrateboot.scheduling.core.SchedulingTaskDefinition;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public final class SchedulingAdminController {
    private final SchedulingAdminService service;

    public SchedulingAdminController(SchedulingAdminService service) {
        this.service = service;
    }

    @GetMapping("${integrate-boot.scheduling.admin.base-path:/integrate/scheduling}/tasks")
    ResultInfo list() { return ResultInfo.success("tasks", service.list()); }

    @PostMapping(value = "${integrate-boot.scheduling.admin.base-path:/integrate/scheduling}/tasks",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ResultInfo save(@RequestBody SchedulingTaskDefinition definition) {
        return ResultInfo.success("task", service.save(definition));
    }

    @DeleteMapping("${integrate-boot.scheduling.admin.base-path:/integrate/scheduling}/tasks/{taskId}")
    ResultInfo delete(@PathVariable String taskId) { service.delete(taskId); return ResultInfo.success(); }

    @PostMapping("${integrate-boot.scheduling.admin.base-path:/integrate/scheduling}/tasks/{taskId}/pause")
    ResultInfo pause(@PathVariable String taskId) { service.pause(taskId); return ResultInfo.success(); }

    @PostMapping("${integrate-boot.scheduling.admin.base-path:/integrate/scheduling}/tasks/{taskId}/resume")
    ResultInfo resume(@PathVariable String taskId) { service.resume(taskId); return ResultInfo.success(); }

    @PostMapping("${integrate-boot.scheduling.admin.base-path:/integrate/scheduling}/tasks/{taskId}/trigger")
    ResultInfo trigger(@PathVariable String taskId) { service.trigger(taskId); return ResultInfo.success(); }
}
