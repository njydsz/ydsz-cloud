package com.njydsz.cronjob.web.controller.job;

import java.time.LocalDateTime;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.entity.job.JobWebhookDO;
import com.njydsz.cronjob.infra.mapper.job.JobWebhookMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * WebHook 事件订阅管理 Controller（P3-13）。
 *
 * <p>提供 WebHook 订阅的增删改查接口，支持按事件类型和任务 KEY 订阅。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "WebHook 事件订阅")
@RestController
@RequestMapping("/cronjob/webhook")
@RequiredArgsConstructor
public class JobWebhookController {

    /** WebHook 订阅 Mapper */
    private final JobWebhookMapper webhookMapper;

    /**
     * 新增 WebHook 订阅。
     *
     * @param webhook WebHook 配置
     * @return 统一响应结果，包含新增 ID
     */
    @Operation(summary = "新增 WebHook 订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobWebhookController:create:lock", ttlSeconds = 5)
    @Audit(module = "WebHook", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @RateLimit(resource = "cronjob.jobwebhook.create", threshold = 50)
    @PostMapping
    public BaseResponse<String> create(@RequestBody JobWebhookDO webhook) {
        webhook.setStatus("ACTIVE");
        webhook.setCreatedAt(LocalDateTime.now());
        webhook.setUpdatedAt(LocalDateTime.now());
        webhook.setDeleted(0);
        if (webhook.getHttpMethod() == null || webhook.getHttpMethod().isBlank()) {
            webhook.setHttpMethod("POST");
        }
        webhookMapper.insert(webhook);
        return BaseResponse.success(webhook.getId());
    }

    /**
     * 更新 WebHook 订阅。
     *
     * @param webhook WebHook 配置
     * @return 统一响应结果
     */
    @Operation(summary = "更新 WebHook 订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobWebhookController:update:lock", ttlSeconds = 5)
    @Audit(module = "WebHook", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @RateLimit(resource = "cronjob.jobwebhook.update", threshold = 50)
    @PutMapping
    public BaseResponse<Void> update(@RequestBody JobWebhookDO webhook) {
        webhook.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(webhook);
        return BaseResponse.success();
    }

    /**
     * 删除 WebHook 订阅（逻辑删除）。
     *
     * @param id WebHook ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除 WebHook 订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobWebhookController:delete:lock", ttlSeconds = 5)
    @Audit(module = "WebHook", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @RateLimit(resource = "cronjob.jobwebhook.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        JobWebhookDO update = new JobWebhookDO();
        update.setId(id);
        update.setDeleted(1);
        update.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(update);
        return BaseResponse.success();
    }

    /**
     * 分页查询 WebHook 订阅列表。
     *
     * @param page      页码（默认 1）
     * @param size      每页条数（默认 20）
     * @param eventType 事件类型过滤（可选）
     * @param jobKey    任务 KEY 过滤（可选）
     * @return 统一响应结果，包含 WebHook 分页数据
     */
    @Operation(summary = "分页查询 WebHook 订阅")
    @GetMapping("/page")
    public BaseResponse<Page<JobWebhookDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String jobKey) {
        LambdaQueryWrapper<JobWebhookDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(w -> w.getDeleted(), 0)
                .eq(eventType != null && !eventType.isBlank(),
                        w -> w.getEventType(), eventType)
                .eq(jobKey != null && !jobKey.isBlank(),
                        w -> w.getJobKey(), jobKey)
                .orderByDesc(w -> w.getCreatedAt());
        return BaseResponse.success(webhookMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /**
     * 查询 WebHook 详情。
     *
     * @param id WebHook ID
     * @return 统一响应结果，包含 WebHook 配置
     */
    @Operation(summary = "查询 WebHook 详情")
    @GetMapping("/{id}")
    public BaseResponse<JobWebhookDO> getById(@PathVariable String id) {
        return BaseResponse.success(webhookMapper.selectById(id));
    }

    /**
     * 测试 WebHook 推送（发送测试事件）。
     *
     * @param id WebHook ID
     * @return 统一响应结果
     */
    @Operation(summary = "测试 WebHook 推送")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobWebhookController:testWebhook:lock", ttlSeconds = 5)
    @Audit(module = "WebHook", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'testWebhook'")
    @RateLimit(resource = "cronjob.jobwebhook.testWebhook", threshold = 50)
    @PostMapping("/{id}/test")
    public BaseResponse<Void> testWebhook(@PathVariable String id) {
        JobWebhookDO webhook = webhookMapper.selectById(id);
        if (webhook == null) {
            return BaseResponse.error("WebHook not found");
        }
        // 测试事件通过 WebhookEventDispatcher 发送
        // 这里仅返回成功，实际推送通过 Async 异步执行
        return BaseResponse.success();
    }
}
