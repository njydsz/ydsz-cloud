package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.dto.FlowDelegateMessageDTO;
import com.njydsz.pmis.workflow.entity.FlowDelegateMessageDO;
import com.njydsz.pmis.workflow.service.FlowDelegateMessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 委派沟通记录 Controller
 *
 * <p>P2-1 (GAP-08): 委托人与被委托人之间的留言沟通接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-delegate-message", description = "工作流委派沟通接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
public class FlowDelegateMessageController {

    private final FlowDelegateMessageService messageService;

    /**
     * 发送委派沟通留言
     */
    @Idempotent(key = "flow-delegate-message:send", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/delegate/message/send")
    public Result<FlowDelegateMessageDO> send(
            @RequestBody FlowDelegateMessageDTO dto,
            @RequestParam String senderId,
            @RequestParam(required = false) String senderName,
            @RequestParam String senderRole,
            @RequestParam(required = false, defaultValue = "1") String tenantId) {
        return Result.ok(messageService.send(dto, senderId, senderName, senderRole, tenantId, null));
    }

    /**
     * 查询任务沟通记录
     */
    @GetMapping("/delegate/message/task/{taskId}")
    public Result<List<FlowDelegateMessageDO>> list(@PathVariable String taskId) {
        return Result.ok(messageService.listByTask(taskId));
    }

    /**
     * 标记已读（当前查看者角色的对侧消息）
     */
    @Idempotent(key = "flow-delegate-message:mark-read", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/delegate/message/read/{taskId}")
    public Result<Void> markRead(@PathVariable String taskId, @RequestParam String viewerRole) {
        messageService.markRead(taskId, viewerRole);
        return Result.ok();
    }
}
