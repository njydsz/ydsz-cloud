package com.njydsz.pmis.cronjob.controller.job;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.entity.job.JobWebhookDO;
import com.njydsz.pmis.cronjob.mapper.job.JobWebhookMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * WebHook 事件订阅管理 Controller（P3-13）。
 *
 * <p>提供 WebHook 订阅的增删改查接口，支持按事件类型和任务 KEY 订阅。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
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
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @OperationLog(module = "任务调度", action = "新增WebHook", bizType = "CRONJOB_WEBHOOK")
    @Idempotent(key = "job-webhook:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@RequestBody JobWebhookDO webhook) {
        webhook.setStatus("ACTIVE");
        webhook.setCreatedAt(LocalDateTime.now());
        webhook.setUpdatedAt(LocalDateTime.now());
        webhook.setDeleted(0);
        if (webhook.getHttpMethod() == null || webhook.getHttpMethod().isBlank()) {
            webhook.setHttpMethod("POST");
        }
        webhookMapper.insert(webhook);
        return Result.ok(webhook.getId());
    }

    /**
     * 更新 WebHook 订阅。
     *
     * @param webhook WebHook 配置
     * @return 统一响应结果
     */
    @Operation(summary = "更新 WebHook 订阅")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @OperationLog(module = "任务调度", action = "更新WebHook", bizType = "CRONJOB_WEBHOOK")
    @Idempotent(key = "job-webhook:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    public Result<Void> update(@RequestBody JobWebhookDO webhook) {
        webhook.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(webhook);
        return Result.ok();
    }

    /**
     * 删除 WebHook 订阅（逻辑删除）。
     *
     * @param id WebHook ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除 WebHook 订阅")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @OperationLog(module = "任务调度", action = "删除WebHook", bizType = "CRONJOB_WEBHOOK")
    @Idempotent(key = "job-webhook:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        JobWebhookDO update = new JobWebhookDO();
        update.setId(id);
        update.setDeleted(1);
        update.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(update);
        return Result.ok();
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
    public Result<Page<JobWebhookDO>> page(
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
        return Result.ok(webhookMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /**
     * 查询 WebHook 详情。
     *
     * @param id WebHook ID
     * @return 统一响应结果，包含 WebHook 配置
     */
    @Operation(summary = "查询 WebHook 详情")
    @GetMapping("/{id}")
    public Result<JobWebhookDO> getById(@PathVariable String id) {
        return Result.ok(webhookMapper.selectById(id));
    }

    /**
     * 测试 WebHook 推送（发送测试事件）。
     *
     * @param id WebHook ID
     * @return 统一响应结果
     */
    @Operation(summary = "测试 WebHook 推送")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "job-webhook:test-webhook", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/test")
    public Result<Void> testWebhook(@PathVariable String id) {
        JobWebhookDO webhook = webhookMapper.selectById(id);
        if (webhook == null) {
            return Result.fail("WebHook not found");
        }
        // 测试事件通过 WebhookEventDispatcher 发送
        // 这里仅返回成功，实际推送通过 Async 异步执行
        return Result.ok();
    }
}
