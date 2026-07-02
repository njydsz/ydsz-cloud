package com.njydsz.pmis.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.scheduler.entity.JobDO;
import com.njydsz.pmis.scheduler.entity.JobLogDO;
import com.njydsz.pmis.scheduler.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 任务调度 Controller
 *
 * <p>提供任务的新增/更新/删除/暂停/恢复/触发/查询/重载等 HTTP 接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "任务调度")
@RestController
@RequestMapping("/api/v1/job")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @Operation(summary = "新增任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_CREATE)
    @PostMapping
    public Result<Long> create(@RequestBody JobDO job) {
        return Result.ok(jobService.create(job));
    }

    @Operation(summary = "更新任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_UPDATE)
    @PutMapping
    public Result<Void> update(@RequestBody JobDO job) {
        jobService.update(job);
        return Result.ok();
    }

    @Operation(summary = "删除任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "暂停任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_UPDATE)
    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id) {
        jobService.pause(id);
        return Result.ok();
    }

    @Operation(summary = "恢复任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_UPDATE)
    @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id) {
        jobService.resume(id);
        return Result.ok();
    }

    @Operation(summary = "立即执行一次")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_TRIGGER)
    @PostMapping("/{id}/trigger")
    public Result<Long> trigger(@PathVariable Long id) {
        return Result.ok(jobService.trigger(id));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<JobDO> getById(@PathVariable Long id) {
        return Result.ok(jobService.getById(id));
    }

    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    public Result<Page<JobDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String group) {
        return Result.ok(jobService.page(page, size, keyword, status, group));
    }

    @Operation(summary = "分页查询任务执行日志")
    @GetMapping("/log/page")
    public Result<Page<JobLogDO>> pageLog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String jobKey,
            @RequestParam(required = false) String status) {
        return Result.ok(jobService.pageLog(page, size, jobKey, status));
    }

    @Operation(summary = "重新加载所有任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_RELOAD)
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        jobService.loadOnStartup();
        return Result.ok(Map.of("message", "ok"));
    }
}
