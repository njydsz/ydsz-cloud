package com.njydsz.pmis.cronjob.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.dto.JobSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/cronjob")
@RequiredArgsConstructor
@Validated
public class JobController {

    /** 任务调度服务 */
    private final JobService jobService;

    /**
     * 新增任务
     *
     * @param job 任务定义
     * @return 统一响应结果，包含新增任务 ID
     */
    @Operation(summary = "新增任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_CREATE)
    @PostMapping
    public Result<String> create(@Valid @RequestBody JobSaveDTO dto) {
        JobDO job = new JobDO();
        BeanUtils.copyProperties(dto, job);
        return Result.ok(jobService.create(job));
    }

    /**
     * 更新任务
     *
     * @param job 任务定义
     * @return 统一响应结果
     */
    @Operation(summary = "更新任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @PutMapping
    public Result<Void> update(@Valid @RequestBody JobSaveDTO dto) {
        JobDO job = new JobDO();
        BeanUtils.copyProperties(dto, job);
        jobService.update(job);
        return Result.ok();
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_DELETE)
    @OperationLog(module = "任务调度", action = "删除任务", bizType = "CRONJOB_JOB")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        jobService.delete(id);
        return Result.ok();
    }

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "暂停任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable String id) {
        jobService.pause(id);
        return Result.ok();
    }

    /**
     * 恢复任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "恢复任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable String id) {
        jobService.resume(id);
        return Result.ok();
    }

    /**
     * 立即执行一次
     *
     * @param id 任务 ID
     * @return 统一响应结果，包含执行日志 ID
     */
    @Operation(summary = "立即执行一次")
    @PrePermission(PermissionCodes.CRONJOB_JOB_TRIGGER)
    @PostMapping("/{id}/trigger")
    public Result<String> trigger(@PathVariable String id) {
        return Result.ok(jobService.trigger(id));
    }

    /**
     * 任务详情
     *
     * @param id 任务 ID
     * @return 统一响应结果，包含任务定义
     */
    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<JobDO> getById(@PathVariable String id) {
        return Result.ok(jobService.getById(id));
    }

    /**
     * 分页查询任务
     *
     * @param page    页码（默认 1）
     * @param size    每页条数（默认 20）
     * @param keyword 关键字（任务名/KEY/处理器，可选）
     * @param status  状态过滤（可选）
     * @param group   分组过滤（可选）
     * @return 统一响应结果，包含任务分页数据
     */
    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    public Result<Page<JobDO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.cronjob.msg_15154512}") @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String group) {
        return Result.ok(jobService.page(page, size, keyword, status, group));
    }

    /**
     * 分页查询任务执行日志
     *
     * @param page   页码（默认 1）
     * @param size   每页条数（默认 20）
     * @param jobKey 任务 KEY 过滤（可选）
     * @param status 状态过滤（可选）
     * @return 统一响应结果，包含执行日志分页数据
     */
    @Operation(summary = "分页查询任务执行日志")
    @GetMapping("/log/page")
    public Result<Page<JobLogDO>> pageLog(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.cronjob.msg_15154512}") @Max(100) int size,
            @RequestParam(required = false) String jobKey,
            @RequestParam(required = false) String status) {
        return Result.ok(jobService.pageLog(page, size, jobKey, status));
    }

    /**
     * 重新加载所有任务
     *
     * @return 统一响应结果，包含操作结果信息
     */
    @Operation(summary = "重新加载所有任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_RELOAD)
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        jobService.loadOnStartup();
        return Result.ok(Map.of("message", "ok"));
    }
}
