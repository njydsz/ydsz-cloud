package com.njydsz.pmis.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.system.dto.MessageSendDTO;
import com.njydsz.pmis.system.entity.MessageLogDO;
import com.njydsz.pmis.system.service.MessageServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

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
@Validated
public class MessageController {

    /** 消息发送服务 */
    private final MessageServiceImpl messageService;

    /**
     * 发送消息（支持模板渲染）
     *
     * @param dto 消息发送参数
     * @return 统一响应结果，包含发送结果
     */
    @Operation(summary = "发送消息（支持模板渲染）")
    @PrePermission("notif:message:send")
    @PostMapping("/send")
    public Result<MessageResult> send(@Valid @RequestBody MessageSendDTO dto) {
        if (dto == null) {
            return Result.failed(10001, "请求不能为空");
        }
        MessageResult r = messageService.send(toRequest(dto));
        return r.isSuccess() ? Result.ok(r, "发送成功") : Result.failed(10001, r.getErrorMessage());
    }

    /**
     * 分页查询消息发送日志
     *
     * @param page    页码
     * @param size    每页大小
     * @param channel 通道（可选）
     * @param bizType 业务类型（可选）
     * @param status  发送状态（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询发送日志")
    @PrePermission("notif:message:send")
    @GetMapping("/log/page")
    public Result<Page<MessageLogDO>> pageLog(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status) {
        return Result.ok(messageService.pageLog(page, size, channel, bizType, status));
    }

    /**
     * 查询已注册的消息通道列表
     *
     * @return 统一响应结果，包含通道类型列表
     */
    @Operation(summary = "已注册通道列表")
    @PrePermission("notif:message:send")
    @GetMapping("/channels")
    public Result<List<String>> channels() {
        return Result.ok(messageService.listChannelTypes());
    }

    /**
     * 将消息发送 DTO 转换为消息请求对象。
     *
     * @param dto 消息发送参数
     * @return 消息请求对象
     */
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
