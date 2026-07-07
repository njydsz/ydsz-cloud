package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.dto.BatchSendResult;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.producer.RocketMQMessageProducer;
import com.njydsz.pmis.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息发送 Controller。
 *
 * <p>提供同步 / 异步两种发送入口：
 * <ul>
 *   <li>{@code /send} / {@code /send-direct}：同步发送，阻塞返回供应商结果</li>
 *   <li>{@code /send-async}：投递到 RocketMQ 异步处理，立即返回 messageId 供追踪</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "消息发送", description = "消息发送与发送日志查询")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    /** RocketMQ 生产者（条件装配，未启用时为空） */
    private final ObjectProvider<RocketMQMessageProducer> producerProvider;

    @Operation(summary = "发送消息(基于共享请求)")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @PostMapping("/send")
    public Result<MessageResult> send(@RequestBody MessageRequest request) {
        return Result.ok(messageService.send(request));
    }

    @Operation(summary = "直接发送消息(本模块 DTO)")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @PostMapping("/send-direct")
    public Result<MessageResult> sendDirect(@RequestBody MessageSendDTO dto) {
        return Result.ok(messageService.sendDirect(dto));
    }

    /**
     * 异步发送：投递到 RocketMQ，由 {@code MessageConsumer} 消费后调用 {@link MessageService#send}。
     * 立即返回 messageId，业务侧可通过 {@code /log/page} 查询最终发送状态。
     *
     * @param request 消息请求
     * @return 含 messageId 的发送结果
     */
    @Operation(summary = "异步发送消息(投递 RocketMQ)")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @PostMapping("/send-async")
    public Result<MessageResult> sendAsync(@RequestBody MessageRequest request) {
        if (request == null) {
            return Result.failed(BizErrorCode.BAD_REQUEST, "消息请求为空");
        }
        RocketMQMessageProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            // 未启用 RocketMQ 时降级为同步发送
            log.warn("[MessageController] RocketMQ 未启用,降级同步发送");
            return Result.ok(messageService.send(request));
        }
        try {
            producer.asyncSend(request);
            // 异步投递成功,返回 messageId 供追踪
            MessageResult result = MessageResult.ok(request.getChannel(), request.getMessageId());
            result.setErrorMessage("ASYNC_QUEUED");
            return Result.ok(result);
        } catch (Exception e) {
            log.error("[MessageController] 异步投递失败,降级同步: err={}", e.getMessage());
            return Result.ok(messageService.send(request));
        }
    }

    @Operation(summary = "发送日志分页")
    @PrePermission(PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/log/page")
    public Result<Page<MsgLogDO>> pageLog(MessageLogQueryDTO query) {
        return Result.ok(messageService.pageLog(query));
    }

    /**
     * 批量发送消息（同步循环,限制 100 条/批）。
     *
     * @param requests 消息请求列表
     * @param batchId  批次 ID（业务侧生成,用于进度查询）
     * @return 批量发送结果
     */
    @Operation(summary = "批量发送消息(限制 100 条/批)")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @PostMapping("/batch-send")
    public Result<BatchSendResult> batchSend(@RequestBody List<MessageRequest> requests,
                                             @RequestParam String batchId) {
        if (requests == null || requests.isEmpty()) {
            return Result.failed(BizErrorCode.BAD_REQUEST, "消息列表为空");
        }
        return Result.ok(messageService.batchSend(requests, batchId));
    }

    /**
     * 查询批次发送进度：按 bizId=batchId 分页查询发送日志。
     *
     * @param batchId 批次 ID
     * @param page    页码
     * @param size    每页大小
     * @return 分页日志
     */
    @Operation(summary = "查询批次发送进度")
    @PrePermission(PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/batch/{batchId}/progress")
    public Result<Page<MsgLogDO>> batchProgress(@PathVariable String batchId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setBizId(batchId);
        query.setPage(page);
        query.setSize(size);
        return Result.ok(messageService.pageLog(query));
    }
}
