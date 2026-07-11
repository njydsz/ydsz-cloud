package com.njydsz.pmis.message.web.controller.receipt;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.dto.receipt.RecallRequestDTO;
import com.njydsz.pmis.message.server.service.receipt.RecallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息撤回 Controller。
 *
 * @author ydsz-pmis-team
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
    @PrePermission(PermissionCodes.MESSAGE_RECALL_ACT)
    @Idempotent(key = "recall:recallNotification", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/notification")
    public Result<Boolean> recallNotification(@RequestParam String userId,
                                              @Valid @RequestBody RecallRequestDTO dto) {
        return Result.ok(recallService.recallNotification(userId, dto.getId()));
    }

    /**
     * 撤回已发送消息。
     *
     * @param logId 发送日志 ID
     * @return 统一响应结果，true 表示撤回成功
     */
    @Operation(summary = "撤回已发送消息")
    @PrePermission(PermissionCodes.MESSAGE_RECALL_ACT)
    @Idempotent(key = "recall:recallMessage", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/message/{logId}")
    public Result<Boolean> recallMessage(@PathVariable String logId) {
        return Result.ok(recallService.recallMessage(logId));
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
    @PrePermission(PermissionCodes.MESSAGE_RECALL_ACT)
    @Idempotent(key = "recall:recallByMsgId", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/msg/{msgId}")
    public Result<Boolean> recallByMsgId(@PathVariable String msgId) {
        return Result.ok(recallService.recallByMsgId(msgId));
    }

    /**
     * 按业务类型和单据 ID 批量撤回消息。
     *
     * @param dto 批量撤回请求体（含 bizType + bizId）
     * @return 统一响应结果，包含撤回条数
     */
    @Operation(summary = "按业务类型+单据 ID 批量撤回")
    @PrePermission(PermissionCodes.MESSAGE_RECALL_ACT)
    @Idempotent(key = "recall:recallBatch", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batch")
    public Result<Integer> recallBatch(@Valid @RequestBody RecallRequestDTO dto) {
        return Result.ok(recallService.recallBatch(dto.getBizType(), dto.getBizId()));
    }
}
