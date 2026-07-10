package com.njydsz.pmis.cronjob.controller;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.dto.job.JobBatchDTO;
import com.njydsz.pmis.cronjob.dto.job.JobSaveDTO;
import com.njydsz.pmis.cronjob.entity.job.JobDO;
import com.njydsz.pmis.cronjob.entity.log.JobLogDO;
import com.njydsz.pmis.cronjob.service.job.JobService;
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
    @Idempotent(key = "job:create", ttlSeconds = 5, message = "请勿重复提交")
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
    @Idempotent(key = "job:update", ttlSeconds = 5, message = "请勿重复提交")
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
    @Idempotent(key = "job:delete", ttlSeconds = 5, message = "请勿重复提交")
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
    @Idempotent(key = "job:pause", ttlSeconds = 5, message = "请勿重复提交")
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
    @Idempotent(key = "job:resume", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable String id) {
        jobService.resume(id);
        return Result.ok();
    }

    /**
     * 立即执行一次
     *
     * @param id 任务 ID
     * @param holdLock 是否抢占分布式锁（默认 false，与历史行为兼容；
     *                 多实例部署下建议传 true 避免与定时触发并发执行）
     * @return 统一响应结果，包含执行日志 ID
     */
    @Operation(summary = "立即执行一次")
    @PrePermission(PermissionCodes.CRONJOB_JOB_TRIGGER)
    @IdempotentExempt("定时触发接口，无需幂等")
    @PostMapping("/{id}/trigger")
    public Result<String> trigger(@PathVariable String id,
                                   @RequestParam(defaultValue = "false") boolean holdLock) {
        return Result.ok(jobService.trigger(id, holdLock));
    }

    /**
     * 批量暂停任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量暂停任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "job:batch-pause", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batch/pause")
    public Result<Integer> batchPause(@RequestBody @Valid JobBatchDTO dto) {
        return Result.ok(jobService.batchPause(dto.getJobIds()));
    }

    /**
     * 批量恢复任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量恢复任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "job:batch-resume", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batch/resume")
    public Result<Integer> batchResume(@RequestBody @Valid JobBatchDTO dto) {
        return Result.ok(jobService.batchResume(dto.getJobIds()));
    }

    /**
     * 批量触发任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量触发任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_TRIGGER)
    @IdempotentExempt("定时触发接口，无需幂等")
    @PostMapping("/batch/trigger")
    public Result<Integer> batchTrigger(@RequestBody @Valid JobBatchDTO dto) {
        return Result.ok(jobService.batchTrigger(dto.getJobIds()));
    }

    /**
     * 批量删除任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量删除任务")
    @PrePermission(PermissionCodes.CRONJOB_JOB_DELETE)
    @OperationLog(module = "任务调度", action = "批量删除任务", bizType = "CRONJOB_JOB")
    @Idempotent(key = "job:batch-delete", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batch/delete")
    public Result<Integer> batchDelete(@RequestBody @Valid JobBatchDTO dto) {
        return Result.ok(jobService.batchDelete(dto.getJobIds()));
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
    @Idempotent(key = "job:reload", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        jobService.loadOnStartup();
        return Result.ok(Map.of("message", "ok"));
    }
}
