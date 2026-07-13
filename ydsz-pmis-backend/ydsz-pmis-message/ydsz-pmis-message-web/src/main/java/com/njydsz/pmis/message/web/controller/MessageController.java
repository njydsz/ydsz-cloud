package com.njydsz.pmis.message.web.controller.core;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.dto.batch.BatchSendResult;
import com.njydsz.pmis.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.pmis.message.domain.dto.core.MessageSendDTO;
import com.njydsz.pmis.message.domain.entity.core.MsgLogDO;
import com.njydsz.pmis.message.server.producer.RocketMQMessageProducer;
import com.njydsz.pmis.message.server.service.core.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    /** 消息发送服务 */
    private final MessageService messageService;
    /** RocketMQ 生产者（条件装配，未启用时为空） */
    private final ObjectProvider<RocketMQMessageProducer> producerProvider;

    /**
     * 基于共享请求发送消息。
     *
     * @param request 消息请求
     * @return 发送结果
     */
    @Operation(summary = "发送消息(基于共享请求)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:send", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/send")
    public BaseResponse<MessageResult> send(@Valid @RequestBody MessageRequest request) {
        return BaseResponse.ok(messageService.send(request));
    }

    /**
     * 直接发送消息（使用本模块 DTO）。
     *
     * @param dto 消息发送请求体
     * @return 发送结果
     */
    @Operation(summary = "直接发送消息(本模块 DTO)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:sendDirect", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/sendDirect")
    public BaseResponse<MessageResult> sendDirect(@Valid @RequestBody MessageSendDTO dto) {
        return BaseResponse.ok(messageService.sendDirect(dto));
    }

    /**
     * 异步发送：投递到 RocketMQ，由 {@code MessageConsumer} 消费后调用 {@link MessageService#send}。
     * 立即返回 messageId，业务侧可通过 {@code /log/page} 查询最终发送状态。
     *
     * @param request 消息请求
     * @return 含 messageId 的发送结果
     */
    @Operation(summary = "异步发送消息(投递 RocketMQ)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:sendAsync", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/sendAsync")
    public BaseResponse<MessageResult> sendAsync(@Valid @RequestBody MessageRequest request) {
        if (request == null) {
            return BaseResponse.failed(StandardResultCode.BAD_REQUEST, "消息请求为空");
        }
        RocketMQMessageProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            // 未启用 RocketMQ 时降级为同步发送
            log.warn("[MessageController] RocketMQ 未启用,降级同步发送");
            return BaseResponse.ok(messageService.send(request));
        }
        try {
            producer.asyncSend(request);
            // 异步投递成功,返回 messageId 供追踪
            MessageResult result = MessageResult.ok(request.getChannel(), request.getMessageId());
            BaseResponse<MessageResult> response = BaseResponse.ok(result);
            response.setMsg("ASYNC_QUEUED");
            return response;
        } catch (Exception e) {
            log.error("[MessageController] 异步投递失败,降级同步: err={}", e.getMessage());
            return BaseResponse.ok(messageService.send(request));
        }
    }

    /**
     * 分页查询发送日志。
     *
     * @param query 日志查询参数
     * @return 日志分页结果
     */
    @Operation(summary = "发送日志分页")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/log/page")
    public BaseResponse<Page<MsgLogDO>> pageLog(MessageLogQueryDTO query) {
        return BaseResponse.ok(messageService.pageLog(query));
    }

    /**
     * P2-3: 事务消息发送（RocketMQ 半消息）。
     *
     * <p>通过 RocketMQ 事务消息机制,确保通知请求仅在本地事务校验（通道/模板有效性）通过后才投递。
     * 未配置 RocketMQ 时降级为同步发送。
     *
     * @param request 消息请求
     * @return 发送结果
     */
    @Operation(summary = "事务消息发送(RocketMQ 半消息)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:sendTransactionally", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/sendTransactional")
    public BaseResponse<MessageResult> sendTransactionally(@Valid @RequestBody MessageRequest request) {
        return BaseResponse.ok(messageService.sendTransactionally(request));
    }

    /**
     * 批量发送消息（同步循环,限制 100 条/批）。
     *
     * @param requests 消息请求列表
     * @param batchId  批次 ID（业务侧生成,用于进度查询）
     * @return 批量发送结果
     */
    @Operation(summary = "批量发送消息(限制 100 条/批)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:batchSend", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batchSend")
    public BaseResponse<BatchSendResult> batchSend(@Valid @RequestBody List<MessageRequest> requests,
                                             @RequestParam String batchId) {
        if (requests == null || requests.isEmpty()) {
            return BaseResponse.failed(StandardResultCode.BAD_REQUEST, "消息列表为空");
        }
        return BaseResponse.ok(messageService.batchSend(requests, batchId));
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
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/batch/{batchId}/progress")
    public BaseResponse<Page<MsgLogDO>> batchProgress(@PathVariable String batchId,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setBizId(batchId);
        query.setPageNum((int) page);
        query.setPageSize((int) size);
        return BaseResponse.ok(messageService.pageLog(query));
    }
}
