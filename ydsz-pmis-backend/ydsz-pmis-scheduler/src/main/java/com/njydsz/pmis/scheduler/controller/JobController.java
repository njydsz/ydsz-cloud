package com.njydsz.pmis.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
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
    public R<Long> create(@RequestBody JobDO job) {
        return R.ok(jobService.create(job));
    }

    @Operation(summary = "更新任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody JobDO job) {
        jobService.update(job);
        return R.ok();
    }

    @Operation(summary = "删除任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return R.ok();
    }

    @Operation(summary = "暂停任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_UPDATE)
    @PostMapping("/{id}/pause")
    public R<Void> pause(@PathVariable Long id) {
        jobService.pause(id);
        return R.ok();
    }

    @Operation(summary = "恢复任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_UPDATE)
    @PostMapping("/{id}/resume")
    public R<Void> resume(@PathVariable Long id) {
        jobService.resume(id);
        return R.ok();
    }

    @Operation(summary = "立即执行一次")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_TRIGGER)
    @PostMapping("/{id}/trigger")
    public R<Long> trigger(@PathVariable Long id) {
        return R.ok(jobService.trigger(id));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public R<JobDO> getById(@PathVariable Long id) {
        return R.ok(jobService.getById(id));
    }

    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    public R<Page<JobDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String group) {
        return R.ok(jobService.page(page, size, keyword, status, group));
    }

    @Operation(summary = "分页查询任务执行日志")
    @GetMapping("/log/page")
    public R<Page<JobLogDO>> pageLog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String jobKey,
            @RequestParam(required = false) String status) {
        return R.ok(jobService.pageLog(page, size, jobKey, status));
    }

    @Operation(summary = "重新加载所有任务")
    @PrePermission(PermissionCodes.SCHEDULER_JOB_RELOAD)
    @PostMapping("/reload")
    public R<Map<String, Object>> reload() {
        jobService.loadOnStartup();
        return R.ok(Map.of("message", "ok"));
    }
}
