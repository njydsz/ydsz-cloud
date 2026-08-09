package com.njydsz.message.web.controller.core;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.batch.BatchSendResult;
import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.core.MessageSendDTO;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.core.MessageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 消息发送 Controller。
 *
 * <p>提供<b>多模态消息发送能力</b>的 HTTP 入口：同步 / 异步 / 事务 / 批量四种发送语义，
 * 是 {@code ydsz-message} 模块的核心门面，被 ydsz-workflow、ydsz-project、ydsz-system
 * 等业务模块通过 Feign（{@code NotificationClient}）远程调用。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>同步发送</b>：{@code POST /send} / {@code /sendDirect} — 阻塞返回供应商结果，适用于通知即时性要求高的场景（如登录验证码）</li>
 *   <li><b>异步发送</b>：{@code POST /sendAsync} — 投递到 RocketMQ，由 {@code MessageConsumer} 消费后真正调用供应商，立即返回 {@code messageId} 用于状态追踪</li>
 *   <li><b>事务消息</b>：{@code POST /sendTransactional} — 基于 RocketMQ 半消息机制，确保通知仅在本地事务校验通过后才投递</li>
 *   <li><b>批量发送</b>：{@code POST /batchSend} — 同步循环（限制 100 条/批），返回 {@code BatchSendResult}</li>
 *   <li><b>日志查询</b>：{@code GET /log/page} — 发送日志分页 / {@code GET /log/batch/{batchId}/page} — 批次进度</li>
 * </ul>
 *
 * <p><b>异步发送落库机制（P0-3）：</b>为保证消息不丢失，
 * 异步发送会先以 {@code PENDING} 状态写入 {@code ydsz_msg_log}，再投递到 MQ；
 * MQ 消费失败时由 {@code DeadLetterController} 处理，避免「发送即丢」。
 *
 * <p><b>多渠道支持：</b>短信（阿里云 / 腾讯云 / 华为云）/ 邮件（QQ 邮箱 / 阿里邮箱）/ 站内信 / 钉钉 / 飞书 / 企业微信 / WebSocket。
 * 渠道路由由 {@code RouteRuleController} 配置。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>所有写接口启用 {@link Idempotent} 5s 防重（Redis SET NX EX）</li>
 *   <li>所有写接口启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>所有写接口启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_MESSAGE_SEND} 等权限码</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.core.MessageService 消息发送服务
 * @see com.njydsz.common.feign.MessageRequest 共享消息请求 DTO
 */
@Slf4j
@Tag(name = "消息发送", description = "消息发送与发送日志查询")
@RestController
@RequestMapping("/api/v1/message")
@RequiredArgsConstructor
public class MessageController {

    /** 消息发送服务 */
    private final MessageService messageService;

    /**
     * 基于共享请求发送消息。
     *
     * @param request 消息请求
     * @return 发送结果
     */
    @Operation(summary = "发送消息(基于共享请求)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "ydsz:message:MessageController:send:lock", ttlSeconds = 5)
    @Audit(module = "消息管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'send'")
    @RateLimit(resource = "message.message.send", threshold = 50)
    @PostMapping("/send")
    public BaseResponse<MessageResult> send(@Valid @RequestBody MessageRequest request) {
        return BaseResponse.success(messageService.send(request));
    }

    /**
     * 直接发送消息（使用本模块 DTO）。
     *
     * @param dto 消息发送请求体
     * @return 发送结果
     */
    @Operation(summary = "直接发送消息(本模块 DTO)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "ydsz:message:MessageController:sendDirect:lock", ttlSeconds = 5)
    @Audit(module = "消息管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'sendDirect'")
    @RateLimit(resource = "message.message.sendDirect", threshold = 50)
    @PostMapping("/sendDirect")
    public BaseResponse<MessageResult> sendDirect(@Valid @RequestBody MessageSendDTO dto) {
        return BaseResponse.success(messageService.sendDirect(dto));
    }

    /**
     * 异步发送：投递到 RocketMQ，由 {@code MessageConsumer} 消费后调用 {@link MessageService#send}。
     * 立即返回 messageId，业务侧可通过 {@code /log/page} 查询最终发送状态。
     *
     * @param request 消息请求
     * @return 含 messageId 的发送结果
     */
    @Operation(summary = "异步发送消息(先落库再投递 MQ)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "ydsz:message:MessageController:sendAsync:lock", ttlSeconds = 5)
    @Audit(module = "消息管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'sendAsync'")
    @RateLimit(resource = "message.message.sendAsync", threshold = 50)
    @PostMapping("/sendAsync")
    public BaseResponse<MessageResult> sendAsync(@Valid @RequestBody MessageRequest request) {
        if (request == null) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "消息请求为空");
        }
        // P0-3: 先落库 PENDING 再投递 MQ，保证消息不丢失
        MessageResult result = messageService.sendAsync(request);
        BaseResponse<MessageResult> response = BaseResponse.success(result);
        response.setMsg("ASYNC_QUEUED");
        return response;
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
    public BaseResponse<Page<MsgLogVO>> pageLog(MessageLogQueryDTO query) {
        Page<MsgLog> page = messageService.pageLog(query);
        Page<MsgLogVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(MessageConverter.INSTANT.logListToVO(page.getRecords()));
        return BaseResponse.success(voPage);
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
    @Idempotent(key = "ydsz:message:MessageController:sendTransactionally:lock", ttlSeconds = 5)
    @Audit(module = "消息管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'sendTransactionally'")
    @RateLimit(resource = "message.message.sendTransactionally", threshold = 50)
    @PostMapping("/sendTransactional")
    public BaseResponse<MessageResult> sendTransactionally(@Valid @RequestBody MessageRequest request) {
        return BaseResponse.success(messageService.sendTransactionally(request));
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
    @Idempotent(key = "ydsz:message:MessageController:batchSend:lock", ttlSeconds = 5)
    @Audit(module = "消息管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchSend'")
    @RateLimit(resource = "message.message.batchSend", threshold = 50)
    @PostMapping("/batchSend")
    public BaseResponse<BatchSendResult> batchSend(@Valid @RequestBody List<MessageRequest> requests,
                                             @RequestParam String batchId) {
        if (requests == null || requests.isEmpty()) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "消息列表为空");
        }
        return BaseResponse.success(messageService.batchSend(requests, batchId));
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
    public BaseResponse<Page<MsgLogVO>> batchProgress(@PathVariable String batchId,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setBizId(batchId);
        query.setPageNum((int) page);
        query.setPageSize((int) size);
        Page<MsgLog> result = messageService.pageLog(query);
        Page<MsgLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(MessageConverter.INSTANT.logListToVO(result.getRecords()));
        return BaseResponse.success(voPage);
    }
}
