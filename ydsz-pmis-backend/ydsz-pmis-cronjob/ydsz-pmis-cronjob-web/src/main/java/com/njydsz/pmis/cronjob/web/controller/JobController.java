package com.njydsz.pmis.cronjob.web.controller.job;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.lock.annotation.IdempotentExempt;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.safe.annotation.RateLimit;
import com.njydsz.pmis.cronjob.domain.dto.job.JobBatchDTO;
import com.njydsz.pmis.cronjob.domain.dto.job.JobSaveDTO;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.pmis.cronjob.server.service.job.JobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

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
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_CREATE)
    @OperationLog(module = "任务调度", action = "新增任务", bizType = "CRONJOB_JOB", saveResult = true)
    @Idempotent(key = "job:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody JobSaveDTO dto) {
        JobDO job = new JobDO();
        BeanUtils.copyProperties(dto, job);
        return BaseResponse.ok(jobService.create(job));
    }

    /**
     * 更新任务
     *
     * @param job 任务定义
     * @return 统一响应结果
     */
    @Operation(summary = "更新任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @OperationLog(module = "任务调度", action = "更新任务", bizType = "CRONJOB_JOB", saveDiff = true)
    @Idempotent(key = "job:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody JobSaveDTO dto) {
        JobDO job = new JobDO();
        BeanUtils.copyProperties(dto, job);
        jobService.update(job);
        return BaseResponse.ok();
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
    @OperationLog(module = "任务调度", action = "删除任务", bizType = "CRONJOB_JOB")
    @Idempotent(key = "job:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        jobService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "暂停任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_PAUSE)
    @OperationLog(module = "任务调度", action = "暂停任务", bizType = "CRONJOB_JOB")
    @Idempotent(key = "job:pause", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/pause")
    public BaseResponse<Void> pause(@PathVariable String id) {
        jobService.pause(id);
        return BaseResponse.ok();
    }

    /**
     * 恢复任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "恢复任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_PAUSE)
    @OperationLog(module = "任务调度", action = "恢复任务", bizType = "CRONJOB_JOB")
    @Idempotent(key = "job:resume", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/resume")
    public BaseResponse<Void> resume(@PathVariable String id) {
        jobService.resume(id);
        return BaseResponse.ok();
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
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_TRIGGER)
    @OperationLog(module = "任务调度", action = "手动触发任务", bizType = "CRONJOB_JOB", saveParams = false)
    @RateLimit(key = "job:trigger", qps = 3, windowSeconds = 60, message = "手动触发过于频繁，请稍后重试")
    @IdempotentExempt("定时触发接口，无需幂等")
    @PostMapping("/{id}/trigger")
    public BaseResponse<String> trigger(@PathVariable String id,
                                   @RequestParam(defaultValue = "false") boolean holdLock) {
        return BaseResponse.ok(jobService.trigger(id, holdLock));
    }

    /**
     * 批量暂停任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量暂停任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "job:batchPause", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batch/pause")
    public BaseResponse<Integer> batchPause(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.ok(jobService.batchPause(dto.getJobIds()));
    }

    /**
     * 批量恢复任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量恢复任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "job:batchResume", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batch/resume")
    public BaseResponse<Integer> batchResume(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.ok(jobService.batchResume(dto.getJobIds()));
    }

    /**
     * 批量触发任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量触发任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_TRIGGER)
    @IdempotentExempt("定时触发接口，无需幂等")
    @PostMapping("/batch/trigger")
    public BaseResponse<Integer> batchTrigger(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.ok(jobService.batchTrigger(dto.getJobIds()));
    }

    /**
     * 批量删除任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量删除任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
    @OperationLog(module = "任务调度", action = "批量删除任务", bizType = "CRONJOB_JOB")
    @Idempotent(key = "job:batchDelete", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batch/delete")
    public BaseResponse<Integer> batchDelete(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.ok(jobService.batchDelete(dto.getJobIds()));
    }

    /**
     * 任务详情
     *
     * @param id 任务 ID
     * @return 统一响应结果，包含任务定义
     */
    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public BaseResponse<JobDO> getById(@PathVariable String id) {
        return BaseResponse.ok(jobService.getById(id));
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
    public BaseResponse<Page<JobDO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.cronjob.msg_15154512}") @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String group) {
        return BaseResponse.ok(jobService.page(page, size, keyword, status, group));
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
    public BaseResponse<Page<JobLogDO>> pageLog(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.cronjob.msg_15154512}") @Max(100) int size,
            @RequestParam(required = false) String jobKey,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(jobService.pageLog(page, size, jobKey, status));
    }

    /**
     * 重新加载所有任务
     *
     * @return 统一响应结果，包含操作结果信息
     */
    @Operation(summary = "重新加载所有任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_RELOAD)
    @Idempotent(key = "job:reload", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/reload")
    public BaseResponse<Map<String, Object>> reload() {
        jobService.loadOnStartup();
        return BaseResponse.ok(Map.of("message", "ok"));
    }
}
