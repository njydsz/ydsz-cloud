package com.njydsz.message.web.controller.core;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.dto.BatchSendResult;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.MessageSendDTO;
import com.njydsz.message.domain.enums.core.SendStrategyEnum;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.core.MessageService;

/**
 * 消息发送 Controller。
 *
 * <p>提供<b>多模态消息发送能力</b>的 HTTP 入口：同步 / 异步 / 事务 / 批量四种发送语义， 是 {@code ydsz-message} 模块的核心门面，被
 * ydsz-workflow、ydsz-project、ydsz-system 等业务模块通过 Feign（{@code NotificationClient}）远程调用。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/**}
 *
 * <p><b>统一发送端点：</b>
 *
 * <ul>
 *   <li><b>统一发送</b>：{@code POST /send} — 通过 DTO 内 {@code strategy} 字段区分 SYNC / DIRECT / ASYNC /
 *       TRANSACTIONAL / BATCH 五种模式
 *   <li><b>日志查询</b>：{@code GET /log/page} — 发送日志分页 / {@code GET /log/batch/{batchId}/page} — 批次进度
 * </ul>
 *
 * <p><b>已废弃端点（已迁移到统一 /send）：</b>
 *
 * <ul>
 *   <li>{@code /sendDirect} → 使用 {@code POST /send} + strategy=DIRECT
 *   <li>{@code /sendAsync} → 使用 {@code POST /send} + strategy=ASYNC
 *   <li>{@code /sendTransactional} → 使用 {@code POST /send} + strategy=TRANSACTIONAL
 *   <li>{@code /batchSend} → 使用 {@code POST /send} + strategy=BATCH
 * </ul>
 *
 * <p><b>异步发送落库机制（P0-3）：</b>为保证消息不丢失， 异步发送会先以 {@code PENDING} 状态写入 {@code ydsz_msg_log}，再投递到 MQ； MQ
 * 消费失败时由 {@code DeadLetterController} 处理，避免「发送即丢」。
 *
 * <p><b>多渠道支持：</b>短信（阿里云 / 腾讯云 / 华为云）/ 邮件（QQ 邮箱 / 阿里邮箱）/ 站内信 / 钉钉 / 飞书 / 企业微信 / WebSocket。 渠道路由由
 * {@code RouteRuleController} 配置。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>所有写接口启用 {@link Idempotent} 5s 防重（Redis SET NX EX）
 *   <li>所有写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>所有写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_MESSAGE_SEND} 等权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.core.MessageService 消息发送服务
 * @see com.njydsz.common.feign.MessageRequest 共享消息请求 DTO
 * @see com.njydsz.message.domain.enums.core.SendStrategyEnum 发送策略枚举
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
   * 统一消息发送入口。
   *
   * <p>通过 DTO 内 {@code strategy} 字段区分五种发送模式：
   *
   * <ul>
   *   <li><b>SYNC</b>：同步发送，阻塞返回供应商结果
   *   <li><b>DIRECT</b>：直接发送，使用本模块 DTO 扩展字段（senderId / messageGroup / locale 等）
   *   <li><b>ASYNC</b>：异步发送，先落库 PENDING 再投递 MQ，返回 msg=ASYNC_QUEUED
   *   <li><b>TRANSACTIONAL</b>：事务消息，RocketMQ 半消息 + 本地事务校验
   *   <li><b>BATCH</b>：批量发送，同步循环限制 100 条/批（需配合 batchRequests + batchId）
   * </ul>
   *
   * @param dto 消息发送请求体（含 strategy 策略字段）
   * @return 发送结果（SYNC/DIRECT/ASYNC/TRANSACTIONAL 返回 MessageResult，BATCH 返回 BatchSendResult）
   */
  @Operation(
      summary = "统一消息发送",
      description = "通过 strategy 字段区分五种发送模式：SYNC（同步）/ DIRECT（直接）/ ASYNC（异步）/ TRANSACTIONAL（事务）/ BATCH（批量）。支持短信/邮件/站内信/钉钉/飞书/企业微信/WebSocket 共 12 种通道。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
  @Idempotent(key = "ydsz:message:MessageController:send:lock", ttlSeconds = 5)
  @Audit(
      module = "消息管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'send strategy=' + #dto.strategy")
  @RateLimit(resource = "message.message.send", threshold = 50)
  @PostMapping("/send")
  public YdszResponse<?> send(@Valid @RequestBody MessageSendDTO dto) {
    SendStrategyEnum strategy = dto.getStrategy();
    if (strategy == null) {
      strategy = SendStrategyEnum.SYNC;
    }

    return switch (strategy) {
      case SYNC -> {
        MessageResult result = messageService.send(toMessageRequest(dto));
        yield YdszResponse.success(result);
      }
      case DIRECT -> {
        MessageResult result = messageService.sendDirect(dto);
        yield YdszResponse.success(result);
      }
      case ASYNC -> {
        // P0-3: 先落库 PENDING 再投递 MQ，保证消息不丢失
        MessageResult result = messageService.sendAsync(toMessageRequest(dto));
        YdszResponse<MessageResult> response = YdszResponse.success(result);
        response.setMsg("ASYNC_QUEUED");
        yield response;
      }
      case TRANSACTIONAL -> {
        MessageResult result = messageService.sendTransactionally(toMessageRequest(dto));
        yield YdszResponse.success(result);
      }
      case BATCH -> {
        List<MessageRequest> requests = dto.getBatchRequests();
        if (requests == null || requests.isEmpty()) {
          yield YdszResponse.error(YdszResultCode.BAD_REQUEST, "批量请求列表为空");
        }
        BatchSendResult result = messageService.batchSend(requests, dto.getBatchId());
        yield YdszResponse.success(result);
      }
    };
  }

  /**
   * 直接发送消息（使用本模块 DTO）。
   *
   * <p><b>已废弃</b>：请统一使用 {@code POST /send} + {@code strategy=DIRECT}。
   *
   * @deprecated 已迁移到统一发送端点 {@link #send(MessageSendDTO)}，设置 {@code strategy=DIRECT} 即可。
   * @param dto 消息发送请求体
   * @return 发送结果
   */
  @Operation(summary = "直接发送消息(本模块 DTO)", description = "已废弃，请统一使用 POST /send + strategy=DIRECT。原功能：同步发送单条消息，使用本模块 MessageSendDTO。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
  @Idempotent(key = "ydsz:message:MessageController:sendDirect:lock", ttlSeconds = 5)
  @Audit(
      module = "消息管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'sendDirect'")
  @RateLimit(resource = "message.message.sendDirect", threshold = 50)
  @Deprecated
  @PostMapping("/sendDirect")
  public YdszResponse<MessageResult> sendDirect(@Valid @RequestBody MessageSendDTO dto) {
    dto.setStrategy(SendStrategyEnum.DIRECT);
    return (YdszResponse<MessageResult>) send(dto);
  }

  /**
   * 异步发送：投递到 RocketMQ，由 {@code MessageConsumer} 消费后调用 {@link MessageService#send}。 立即返回
   * messageId，业务侧可通过 {@code /log/page} 查询最终发送状态。
   *
   * <p><b>已废弃</b>：请统一使用 {@code POST /send} + {@code strategy=ASYNC}。
   *
   * @deprecated 已迁移到统一发送端点 {@link #send(MessageSendDTO)}，设置 {@code strategy=ASYNC} 即可。
   * @param request 消息请求
   * @return 含 messageId 的发送结果
   */
  @Operation(summary = "异步发送消息(先落库再投递 MQ)", description = "已废弃，请统一使用 POST /send + strategy=ASYNC。原功能：异步发送单条消息，先将消息以 PENDING 状态落库保证不丢失，再投递到 RocketMQ。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
  @Idempotent(key = "ydsz:message:MessageController:sendAsync:lock", ttlSeconds = 5)
  @Audit(
      module = "消息管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'sendAsync'")
  @RateLimit(resource = "message.message.sendAsync", threshold = 50)
  @Deprecated
  @PostMapping("/sendAsync")
  public YdszResponse<MessageResult> sendAsync(@Valid @RequestBody MessageRequest request) {
    MessageSendDTO dto = new MessageSendDTO();
    dto.setStrategy(SendStrategyEnum.ASYNC);
    fillFromRequest(dto, request);
    return (YdszResponse<MessageResult>) send(dto);
  }

  /**
   * 分页查询发送日志。
   *
   * @param query 日志查询参数
   * @return 日志分页结果
   */
  @Operation(summary = "发送日志分页", description = "分页查询消息发送日志。支持按 bizId、channelCode、status、时间范围等条件过滤。返回分页结果含 MsgLogVO（消息 ID、通道、接收人、状态、回执 ID、发送时间）。")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
  @GetMapping("/log/page")
  public PageResponse<List<MsgLogVO>> pageLog(MessageLogQueryDTO query) {
    return messageService.pageLog(query);
  }

  /**
   * P1-F3: 取消定时消息（仅允许取消状态为 SCHEDULED 的消息）。
   *
   * @param msgId 定时消息 ID（发送定时消息时返回的 messageId）
   * @return 取消结果
   */
  @Operation(summary = "取消定时消息", description = "取消已调度但尚未发送的定时消息。仅允许取消状态为 SCHEDULED 的消息，通过 msgId（发送定时消息时返回的 messageId）定位。取消成功后消息状态变为 CANCELLED。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
  @Audit(
      module = "消息管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'取消定时消息: msgId=' + #msgId")
  @PostMapping("/cancelScheduled")
  public YdszResponse<MessageResult> cancelScheduled(@RequestParam String msgId) {
    return YdszResponse.success(messageService.cancelScheduledMessage(msgId));
  }

  /**
   * P2-3: 事务消息发送（RocketMQ 半消息）。
   *
   * <p>通过 RocketMQ 事务消息机制,确保通知请求仅在本地事务校验（通道/模板有效性）通过后才投递。 未配置 RocketMQ 时降级为同步发送。
   *
   * <p><b>已废弃</b>：请统一使用 {@code POST /send} + {@code strategy=TRANSACTIONAL}。
   *
   * @deprecated 已迁移到统一发送端点 {@link #send(MessageSendDTO)}，设置 {@code strategy=TRANSACTIONAL} 即可。
   * @param request 消息请求
   * @return 发送结果
   */
  @Operation(summary = "事务消息发送(RocketMQ 半消息)", description = "已废弃，请统一使用 POST /send + strategy=TRANSACTIONAL。原功能：基于 RocketMQ 事务消息机制（半消息）发送通知。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
  @Idempotent(key = "ydsz:message:MessageController:sendTransactionally:lock", ttlSeconds = 5)
  @Audit(
      module = "消息管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'sendTransactionally'")
  @RateLimit(resource = "message.message.sendTransactionally", threshold = 50)
  @Deprecated
  @PostMapping("/sendTransactional")
  public YdszResponse<MessageResult> sendTransactionally(
      @Valid @RequestBody MessageRequest request) {
    MessageSendDTO dto = new MessageSendDTO();
    dto.setStrategy(SendStrategyEnum.TRANSACTIONAL);
    fillFromRequest(dto, request);
    return (YdszResponse<MessageResult>) send(dto);
  }

  /**
   * 批量发送消息（同步循环,限制 100 条/批）。
   *
   * <p><b>已废弃</b>：请统一使用 {@code POST /send} + {@code strategy=BATCH}，并在 DTO 中携带 {@code batchRequests} +
   * {@code batchId}。
   *
   * @deprecated 已迁移到统一发送端点 {@link #send(MessageSendDTO)}，设置 {@code strategy=BATCH} 并携带 batchRequests +
   *             batchId 即可。
   * @param requests 消息请求列表
   * @param batchId 批次 ID（业务侧生成,用于进度查询）
   * @return 批量发送结果
   */
  @Operation(summary = "批量发送消息(限制 100 条/批)", description = "已废弃，请统一使用 POST /send + strategy=BATCH。原功能：同步批量发送消息，逐条循环发送。单次请求最多 100 条。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
  @Idempotent(key = "ydsz:message:MessageController:batchSend:lock", ttlSeconds = 5)
  @Audit(
      module = "消息管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchSend'")
  @RateLimit(resource = "message.message.batchSend", threshold = 50)
  @Deprecated
  @PostMapping("/batchSend")
  public YdszResponse<BatchSendResult> batchSend(
      @Valid @RequestBody List<MessageRequest> requests, @RequestParam String batchId) {
    MessageSendDTO dto = new MessageSendDTO();
    dto.setStrategy(SendStrategyEnum.BATCH);
    dto.setBatchRequests(requests);
    dto.setBatchId(batchId);
    return (YdszResponse<BatchSendResult>) send(dto);
  }

  /**
   * 查询批次发送进度：按 bizId=batchId 分页查询发送日志。
   *
   * @param batchId 批次 ID
   * @param page 页码
   * @param size 每页大小
   * @return 分页日志
   */
  @Operation(summary = "查询批次发送进度", description = "按批次 ID 分页查询发送日志，用于追踪批量发送任务的执行进度。返回分页结果含各消息当前状态（PENDING/SENT/FAILED）、通道、接收人、回执 ID。")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
  @GetMapping("/batch/{batchId}/progress")
  public PageResponse<List<MsgLogVO>> batchProgress(
      @PathVariable String batchId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long size) {
    MessageLogQueryDTO query = new MessageLogQueryDTO();
    query.setBizId(batchId);
    query.setPageNum((int) page);
    query.setPageSize((int) size);
    return messageService.pageLog(query);
  }

  // ===== 私有辅助方法 =====

  /**
   * 将 MessageSendDTO 转换为 MessageRequest（用于 SYNC / ASYNC / TRANSACTIONAL 模式）。
   *
   * @param dto 本模块 DTO
   * @return 共享请求 DTO
   */
  private MessageRequest toMessageRequest(MessageSendDTO dto) {
    MessageRequest request = new MessageRequest();
    request.setChannel(dto.getChannel());
    request.setReceiver(dto.getReceiver());
    request.setSubject(dto.getSubject());
    request.setContent(dto.getContent());
    request.setBizType(dto.getBizType());
    request.setBizId(dto.getBizId());
    request.setTemplateCode(dto.getTemplateCode());
    request.setParams(dto.getParams());
    request.setPriority(dto.getPriority());
    request.setMessageId(dto.getMessageId());
    return request;
  }

  /**
   * 从 MessageRequest 填充 MessageSendDTO 的共有字段（用于废弃端点的委托转发）。
   *
   * @param dto 本模块 DTO（strategy 已设置）
   * @param request 共享请求 DTO
   */
  private void fillFromRequest(MessageSendDTO dto, MessageRequest request) {
    dto.setChannel(request.getChannel());
    dto.setReceiver(request.getReceiver());
    dto.setSubject(request.getSubject());
    dto.setContent(request.getContent());
    dto.setBizType(request.getBizType());
    dto.setBizId(request.getBizId());
    dto.setTemplateCode(request.getTemplateCode());
    dto.setParams(request.getParams());
    dto.setPriority(request.getPriority());
    dto.setMessageId(request.getMessageId());
  }
}
