package com.njydsz.message.web.controller.receipt;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.receipt.ReceiptCallbackDTO;
import com.njydsz.message.domain.entity.receipt.MsgReceipt;
import com.njydsz.message.server.service.receipt.ReceiptService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 消息回执 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "消息回执", description = "服务商回执回调与查询")
@RestController
@RequestMapping("/api/v1/message/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    /** 消息回执服务 */
    private final ReceiptService receiptService;

    /**
     * 服务商回执回调接口。
     *
     * @param dto 回执回调请求体
     * @return 统一响应结果
     */
    @Operation(summary = "回执回调")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECEIPT_CALLBACK)
    @Idempotent(key = "ydsz:message:ReceiptController:callback:lock", ttlSeconds = 5)
    @Audit(module = "消息回执", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'callback'")
    @RateLimit(resource = "message.receipt.callback", threshold = 50)
    @PostMapping("/callback")
    public BaseResponse<Void> callback(@Valid @RequestBody ReceiptCallbackDTO dto) {
        receiptService.callback(dto);
        return BaseResponse.success();
    }

    /**
     * 按发送日志 ID 查询回执列表。
     *
     * @param logId 发送日志 ID
     * @return 统一响应结果，包含回执列表
     */
    @Operation(summary = "按日志 ID 查询回执列表")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECEIPT_VIEW)
    @GetMapping("/{logId}")
    public BaseResponse<List<MsgReceipt>> listByLogId(@PathVariable String logId) {
        return BaseResponse.success(receiptService.listByLogId(logId));
    }
}
