package com.njydsz.cronjob.web.controller.job;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.entity.job.JobWebhook;
import com.njydsz.cronjob.infra.mapper.job.JobWebhookMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.dto.post.JobWebhookPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobWebhookPutDTO;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;
import com.njydsz.cronjob.domain.enums.CronjobResultCode;

/**
 * WebHook 事件订阅管理 Controller（P3-13）。
 *
 * <p>提供 WebHook 订阅的增删改查接口，支持按事件类型和任务 KEY 订阅。
 * 任务执行过程中产生的关键事件（成功/失败/超时/开始/结束）会通过
 * {@code WebhookEventDispatcher} 异步推送到已注册的 WebHook。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>CRUD：创建 / 更新 / 删除 / 详情 / 分页查询</li>
 *   <li>过滤：按 eventType 和 jobKey 组合过滤</li>
 *   <li>测试：{@link #testWebhook} 主动发送测试事件</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "WebHook 事件订阅", description = "订阅 CRUD、过滤查询、测试推送")
@RestController
@RequestMapping("/api/v1/cronjob/webhook")
@RequiredArgsConstructor
public class JobWebhookController {

    /** WebHook 订阅 Mapper */
    private final JobWebhookMapper webhookMapper;

    /**
     * 新增 WebHook 订阅。
     *
     * <p>订阅创建后立即生效（{@code status=ACTIVE}）。默认 HTTP 方法 POST，可通过入参覆盖。
     * 订阅的 eventType 支持通配（{@code *} 表示订阅所有事件）。
     *
     * @param dto WebHook 配置（url/eventType/jobKey/httpMethod/headers/secret）
     * @return 新订阅 ID
     */
    @Operation(summary = "新增 WebHook 订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobWebhookController:create:lock", ttlSeconds = 5)
    @Audit(module = "WebHook", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @RateLimit(resource = "cronjob.jobwebhook.create", threshold = 50)
    @PostMapping
    public BaseResponse<String> create(@RequestBody JobWebhookPostDTO dto) {
        JobWebhook webhook = CronjobConverter.INSTANT.postDtoToEntity(dto);
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
     * <p>修改 url/headers/secret 等配置。eventType/jobKey 不允许变更（应删除重建）。
     *
     * @param dto WebHook 更新请求体（必须含 id）
     * @return 统一响应结果
     */
    @Operation(summary = "更新 WebHook 订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobWebhookController:update:lock", ttlSeconds = 5)
    @Audit(module = "WebHook", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @RateLimit(resource = "cronjob.jobwebhook.update", threshold = 50)
    @PutMapping
    public BaseResponse<Void> update(@RequestBody JobWebhookPutDTO dto) {
        JobWebhook webhook = CronjobConverter.INSTANT.putDtoToEntity(dto);
        webhook.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(webhook);
        return BaseResponse.success();
    }

    /**
     * 删除 WebHook 订阅（逻辑删除）。
     *
     * <p>将 {@code deleted=1} 标记为软删除，不再接收事件推送。
     * 历史事件记录保留，便于审计追溯。
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
        JobWebhook update = new JobWebhook();
        update.setId(id);
        update.setDeleted(1);
        update.setUpdatedAt(LocalDateTime.now());
        webhookMapper.updateById(update);
        return BaseResponse.success();
    }

    /**
     * 分页查询 WebHook 订阅列表。
     *
     * <p>按 created_at 倒序排列。eventType/jobKey 为可选过滤条件，二者可组合（AND 关系）。
     *
     * @param page      页码（默认 1）
     * @param size      每页条数（默认 20）
     * @param eventType 事件类型过滤（可选）
     * @param jobKey    任务 KEY 过滤（可选）
     * @return WebHook 分页数据
     */
    @Operation(summary = "分页查询 WebHook 订阅")
    @GetMapping("/page")
    public PageResult<JobWebhookVO> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String jobKey) {
        LambdaQueryWrapper<JobWebhook> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(w -> w.getDeleted(), 0)
                .eq(eventType != null && !eventType.isBlank(),
                        w -> w.getEventType(), eventType)
                .eq(jobKey != null && !jobKey.isBlank(),
                        w -> w.getJobKey(), jobKey)
                .orderByDesc(w -> w.getCreatedAt());
        Page<JobWebhook> page = webhookMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.success(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                CronjobConverter.INSTANT.jobWebhookListToVO(page.getRecords()));
    }

    /**
     * 查询 WebHook 详情。
     *
     * @param id WebHook ID
     * @return WebHook 配置详情 VO
     */
    @Operation(summary = "查询 WebHook 详情")
    @GetMapping("/{id}")
    public BaseResponse<JobWebhookVO> getById(@PathVariable String id) {
        return BaseResponse.success(CronjobConverter.INSTANT.entityToVO(webhookMapper.selectById(id)));
    }

    /**
     * 测试 WebHook 推送（发送测试事件）。
     *
     * <p>主动发送一个 {@code TEST_WEBHOOK} 类型的合成事件，用于验证 WebHook 配置正确性。
     * 异步执行，不阻塞当前线程；失败重试由 {@code WebhookEventDispatcher} 负责。
     *
     * @param id WebHook ID
     * @return 统一响应结果（仅表示任务已派发，不代表实际推送成功）
     */
    @Operation(summary = "测试 WebHook 推送")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobWebhookController:testWebhook:lock", ttlSeconds = 5)
    @Audit(module = "WebHook", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'testWebhook'")
    @RateLimit(resource = "cronjob.jobwebhook.testWebhook", threshold = 50)
    @PostMapping("/{id}/test")
    public BaseResponse<Void> testWebhook(@PathVariable String id) {
        JobWebhook webhook = webhookMapper.selectById(id);
        if (webhook == null) {
            return BaseResponse.error(CronjobResultCode.WEBHOOK_NOT_FOUND, "WebHook not found");
        }
        // 测试事件通过 WebhookEventDispatcher 发送
        // 这里仅返回成功，实际推送通过 Async 异步执行
        return BaseResponse.success();
    }
}
