package com.njydsz.cronjob.web.controller.job;

import java.util.Map;

import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.annotation.RateLimit;
import com.njydsz.cronjob.domain.dto.job.JobBatchDTO;
import com.njydsz.cronjob.domain.dto.job.JobSaveDTO;
import com.njydsz.cronjob.domain.entity.job.JobDO;
import com.njydsz.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.cronjob.server.service.job.JobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 任务调度 Controller
 *
 * <p>提供任务的新增/更新/删除/暂停/恢复/触发/查询/重载等 HTTP 接口。
 *
 * @author ydsz-team
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
    @Idempotent(key = "job:create", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @SentinelRateLimit(resource = "cronjob.job.create", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.create", threshold = 50)
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody JobSaveDTO dto) {
        JobDO job = new JobDO();
        BeanUtils.copyProperties(dto, job);
        return BaseResponse.success(jobService.create(job));
    }

    /**
     * 更新任务
     *
     * @param job 任务定义
     * @return 统一响应结果
     */
    @Operation(summary = "更新任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "job:update", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @SentinelRateLimit(resource = "cronjob.job.update", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.update", threshold = 50)
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody JobSaveDTO dto) {
        JobDO job = new JobDO();
        BeanUtils.copyProperties(dto, job);
        jobService.update(job);
        return BaseResponse.success();
    }

    /**
     * P1-B1+B2: Cron 表达式校验 + 下次触发时间预览。
     *
     * <p>对标 XXL-Job 的 cronCheck 端点，在保存任务前验证 Cron 表达式合法性，
     * 并返回下次 N 次触发时间，帮助用户确认调度频率正确。
     *
     * @param expr Cron 表达式
     * @param count 预览次数（默认 5）
     * @return 统一响应结果，包含校验结果和下次触发时间列表
     */
    @Operation(summary = "Cron 表达式校验 + 触发时间预览")
    @GetMapping("/cron/validate")
    public BaseResponse<Map<String, Object>> validateCron(
            @RequestParam String expr,
            @RequestParam(defaultValue = "5") int count) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            org.springframework.scheduling.support.CronExpression cron =
                    org.springframework.scheduling.support.CronExpression.parse(expr);
            result.put("valid", true);
            java.util.List<String> nextFireTimes = new java.util.ArrayList<>();
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            for (int i = 0; i < count; i++) {
                now = cron.next(now);
                if (now == null) {
                    break;
                }
                nextFireTimes.add(now.toString());
            }
            result.put("nextFireTimes", nextFireTimes);
        } catch (IllegalArgumentException e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
        return BaseResponse.success(result);
    }

    /**
     * P1-B5: 批量删除任务。
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量删除任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
    @Idempotent(key = "job:batchDelete", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchDelete'")
    @SentinelRateLimit(resource = "cronjob.job.batchDelete", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.batchDelete", threshold = 50)
    @PostMapping("/batch/delete")
    public BaseResponse<Integer> batchDelete(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchDelete(dto.getJobIds()));
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
    @Idempotent(key = "job:delete", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @SentinelRateLimit(resource = "cronjob.job.delete", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        jobService.delete(id);
        return BaseResponse.success();
    }

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "暂停任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_PAUSE)
    @Idempotent(key = "job:pause", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'pause'")
    @SentinelRateLimit(resource = "cronjob.job.pause", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.pause", threshold = 50)
    @PostMapping("/{id}/pause")
    public BaseResponse<Void> pause(@PathVariable String id) {
        jobService.pause(id);
        return BaseResponse.success();
    }

    /**
     * 恢复任务
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "恢复任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_PAUSE)
    @Idempotent(key = "job:resume", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'resume'")
    @SentinelRateLimit(resource = "cronjob.job.resume", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.resume", threshold = 50)
    @PostMapping("/{id}/resume")
    public BaseResponse<Void> resume(@PathVariable String id) {
        jobService.resume(id);
        return BaseResponse.success();
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
    @RateLimit(key = "job:trigger", qps = 3, windowSeconds = 60, message = "手动触发过于频繁，请稍后重试")
    @IdempotentExempt("定时触发接口，无需幂等")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'trigger'")
    @PostMapping("/{id}/trigger")
    public BaseResponse<String> trigger(@PathVariable String id,
                                   @RequestParam(defaultValue = "false") boolean holdLock) {
        return BaseResponse.success(jobService.trigger(id, holdLock));
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
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchPause'")
    @SentinelRateLimit(resource = "cronjob.job.batchPause", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.batchPause", threshold = 50)
    @PostMapping("/batch/pause")
    public BaseResponse<Integer> batchPause(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchPause(dto.getJobIds()));
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
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchResume'")
    @SentinelRateLimit(resource = "cronjob.job.batchResume", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.batchResume", threshold = 50)
    @PostMapping("/batch/resume")
    public BaseResponse<Integer> batchResume(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchResume(dto.getJobIds()));
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
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchTrigger'")
    @SentinelRateLimit(resource = "cronjob.job.batchTrigger", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.batchTrigger", threshold = 50)
    @PostMapping("/batch/trigger")
    public BaseResponse<Integer> batchTrigger(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchTrigger(dto.getJobIds()));
    }

    /**
     * 批量删除任务
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 统一响应结果，包含成功处理的数量
     */
    @Operation(summary = "批量删除任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
    @Idempotent(key = "job:batchDelete", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchDelete'")
    @SentinelRateLimit(resource = "cronjob.job.batchDelete", threshold = 50)
    @SentinelRateLimit(resource = "cronjob.job.batchDelete", threshold = 50)
    @PostMapping("/batch/delete")
    public BaseResponse<Integer> batchDelete(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchDelete(dto.getJobIds()));
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
        return BaseResponse.success(jobService.getById(id));
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
        return BaseResponse.success(jobService.page(page, size, keyword, status, group));
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
        return BaseResponse.success(jobService.pageLog(page, size, jobKey, status));
    }

    /**
     * 重新加载所有任务
     *
     * @return 统一响应结果，包含操作结果信息
     */
    @Operation(summary = "重新加载所有任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_RELOAD)
    @Idempotent(key = "job:reload", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @PostMapping("/reload")
    public BaseResponse<Map<String, Object>> reload() {
        jobService.loadOnStartup();
        return BaseResponse.success(Map.of("message", "ok"));
    }
}
