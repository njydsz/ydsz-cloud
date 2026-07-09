package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.dto.RecallRequestDTO;
import com.njydsz.pmis.message.service.RecallService;
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

    private final RecallService recallService;

    @Operation(summary = "撤回站内通知")
    @PrePermission(PermissionCodes.MESSAGE_RECALL_ACT)
    @PostMapping("/notification")
    public Result<Boolean> recallNotification(@RequestParam String userId,
                                              @Valid @RequestBody RecallRequestDTO dto) {
        return Result.ok(recallService.recallNotification(userId, dto.getId()));
    }

    @Operation(summary = "撤回已发送消息")
    @PrePermission(PermissionCodes.MESSAGE_RECALL_ACT)
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
    @PostMapping("/msg/{msgId}")
    public Result<Boolean> recallByMsgId(@PathVariable String msgId) {
        return Result.ok(recallService.recallByMsgId(msgId));
    }

    @Operation(summary = "按业务类型+单据 ID 批量撤回")
    @PrePermission(PermissionCodes.MESSAGE_RECALL_ACT)
    @PostMapping("/batch")
    public Result<Integer> recallBatch(@Valid @RequestBody RecallRequestDTO dto) {
        return Result.ok(recallService.recallBatch(dto.getBizType(), dto.getBizId()));
    }
}
