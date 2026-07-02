package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.entity.MessageLogDO;
import com.njydsz.pmis.message.service.MessageServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息发送 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "消息发送")
@RestController
@RequestMapping("/api/v1/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageServiceImpl messageService;

    @Operation(summary = "发送消息（支持模板渲染）")
    @PrePermission("notif:message:send")
    @PostMapping("/send")
    public Result<MessageResult> send(@RequestBody MessageSendDTO dto) {
        if (dto == null) {
            return Result.failed(10001, "请求不能为空");
        }
        MessageResult r = messageService.send(toRequest(dto));
        return r.isSuccess() ? Result.ok(r, "发送成功") : Result.failed(10001, r.getErrorMessage());
    }

    @Operation(summary = "分页查询发送日志")
    @PrePermission("notif:message:send")
    @GetMapping("/log/page")
    public Result<Page<MessageLogDO>> pageLog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status) {
        return Result.ok(messageService.pageLog(page, size, channel, bizType, status));
    }

    @Operation(summary = "已注册通道列表")
    @PrePermission("notif:message:send")
    @GetMapping("/channels")
    public Result<List<String>> channels() {
        return Result.ok(messageService.listChannelTypes());
    }

    private MessageRequest toRequest(MessageSendDTO dto) {
        MessageRequest req = new MessageRequest();
        req.setChannel(dto.getChannel());
        req.setTemplateCode(dto.getTemplateCode());
        req.setReceiver(dto.getReceiver());
        req.setParams(dto.getParams());
        req.setContent(dto.getContent());
        req.setSubject(dto.getSubject());
        req.setBizType(dto.getBizType());
        req.setBizId(dto.getBizId());
        return req;
    }
}
