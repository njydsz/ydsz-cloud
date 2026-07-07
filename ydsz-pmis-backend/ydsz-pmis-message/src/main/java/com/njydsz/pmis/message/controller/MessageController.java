package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息发送 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "消息发送", description = "消息发送与发送日志查询")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "发送消息(基于共享请求)")
    @PostMapping("/send")
    public Result<MessageResult> send(@RequestBody com.njydsz.pmis.common.feign.MessageRequest request) {
        // TODO 权限码
        return Result.ok(messageService.send(request));
    }

    @Operation(summary = "直接发送消息(本模块 DTO)")
    @PostMapping("/send-direct")
    public Result<MessageResult> sendDirect(@RequestBody MessageSendDTO dto) {
        // TODO 权限码
        return Result.ok(messageService.sendDirect(dto));
    }

    @Operation(summary = "发送日志分页")
    @GetMapping("/log/page")
    public Result<Page<MsgLogDO>> pageLog(MessageLogQueryDTO query) {
        // TODO 权限码
        return Result.ok(messageService.pageLog(query));
    }
}
