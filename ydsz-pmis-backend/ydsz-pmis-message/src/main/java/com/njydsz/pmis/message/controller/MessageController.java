package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.message.channel.MessageResult;
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
    @PostMapping("/send")
    public R<MessageResult> send(@RequestBody MessageSendDTO dto) {
        if (dto == null) {
            return R.failed(10001, "请求不能为空");
        }
        MessageResult r = messageService.send(toRequest(dto));
        return r.isSuccess() ? R.ok(r, "发送成功") : R.failed(10001, r.getErrorMessage());
    }

    @Operation(summary = "分页查询发送日志")
    @GetMapping("/log/page")
    public R<Page<MessageLogDO>> pageLog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status) {
        return R.ok(messageService.pageLog(page, size, channel, bizType, status));
    }

    @Operation(summary = "已注册通道列表")
    @GetMapping("/channels")
    public R<List<String>> channels() {
        return R.ok(messageService.listChannelTypes());
    }

    private com.njydsz.pmis.message.channel.MessageRequest toRequest(MessageSendDTO dto) {
        com.njydsz.pmis.message.channel.MessageRequest req = new com.njydsz.pmis.message.channel.MessageRequest();
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
