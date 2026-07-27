package com.njydsz.message.web.controller.receipt;

import jakarta.validation.Valid;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.receipt.RecallRequestDTO;
import com.njydsz.message.server.service.receipt.RecallService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 消息撤回 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "消息撤回", description = "通知/消息撤回")
@RestController
@RequestMapping("/message/recall")
@RequiredArgsConstructor
public class RecallController {

    /** 消息撤回服务 */
    private final RecallService recallService;

    /**
     * 撤回站内通知。
     *
     * @param userId 用户 ID
     * @param dto    撤回请求体（含通知 ID）
     * @return 统一响应结果，true 表示撤回成功
     */
    @Operation(summary = "撤回站内通知")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECALL_ACT)
    @Idempotent(key = "ydsz:message:RecallController:recallNotification:lock", ttlSeconds = 5)
    @Audit(module = "消息撤回", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'recallNotification'")
    @RateLimit(resource = "message.recall.recallNotification", threshold = 50)
    @PostMapping("/notification")
    public BaseResponse<Boolean> recallNotification(@RequestParam String userId,
                                              @Valid @RequestBody RecallRequestDTO dto) {
        return BaseResponse.success(recallService.recallNotification(userId, dto.getId()));
    }

    /**
     * 撤回已发送消息。
     *
     * @param logId 发送日志 ID
     * @return 统一响应结果，true 表示撤回成功
     */
    @Operation(summary = "撤回已发送消息")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECALL_ACT)
    @Idempotent(key = "ydsz:message:RecallController:recallMessage:lock", ttlSeconds = 5)
    @Audit(module = "消息撤回", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'recallMessage'")
    @RateLimit(resource = "message.recall.recallMessage", threshold = 50)
    @PostMapping("/message/{logId}")
    public BaseResponse<Boolean> recallMessage(@PathVariable String logId) {
        return BaseResponse.success(recallService.recallMessage(logId));
    }

    /**
     * P0-4: 按 msgId 撤回已发送消息。
     *
     * <p>支持撤回时间窗口校验（默认 30 分钟内可撤回）。
     *
     * @param msgId 消息 ID
     * @return 撤回结果
     */
    @Operation(summary = "按消息 ID 撤回消息")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECALL_ACT)
    @Idempotent(key = "ydsz:message:RecallController:recallByMsgId:lock", ttlSeconds = 5)
    @Audit(module = "消息撤回", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'recallByMsgId'")
    @RateLimit(resource = "message.recall.recallByMsgId", threshold = 50)
    @PostMapping("/msg/{msgId}")
    public BaseResponse<Boolean> recallByMsgId(@PathVariable String msgId) {
        return BaseResponse.success(recallService.recallByMsgId(msgId));
    }

    /**
     * 按业务类型和单据 ID 批量撤回消息。
     *
     * @param dto 批量撤回请求体（含 bizType + bizId）
     * @return 统一响应结果，包含撤回条数
     */
    @Operation(summary = "按业务类型+单据 ID 批量撤回")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECALL_ACT)
    @Idempotent(key = "ydsz:message:RecallController:recallBatch:lock", ttlSeconds = 5)
    @Audit(module = "消息撤回", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'recallBatch'")
    @RateLimit(resource = "message.recall.recallBatch", threshold = 50)
    @PostMapping("/batch")
    public BaseResponse<Integer> recallBatch(@Valid @RequestBody RecallRequestDTO dto) {
        return BaseResponse.success(recallService.recallBatch(dto.getBizType(), dto.getBizId()));
    }
}
