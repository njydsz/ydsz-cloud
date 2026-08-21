package com.njydsz.message.web.controller.core;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.feign.dto.BroadcastRequestDTO;
import com.njydsz.common.feign.dto.PushRealtimeRequestDTO;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.socket.trace.WebSocketTraceContext;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.dto.NotificationSendDTO;
import com.njydsz.message.infra.entity.MsgNotification;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.NotificationService;
import com.njydsz.message.server.service.receipt.RecallService;

/**
 * 站内通知 Controller。
 *
 * <p>提供<b>站内通知</b>的完整生命周期 HTTP API：发送 → 收件箱 → 已读 → 撤回 → 实时推送。 站内通知是 ydsz-message 的核心通知类型之一，与短信 / 邮件
 * / 钉钉 / 飞书 / 企业微信 / WebSocket 并列。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/notifications/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>发送</b>：{@code POST /} 单条发送 / {@code POST /batch} 批量发送
 *   <li><b>收件箱</b>：{@code GET /page} 我的通知分页 / {@code GET /unread-count} 未读数（导航栏徽标）
 *   <li><b>已读机制</b>：{@code POST /{id}/read} 标记单条已读 / {@code POST /read-all} 全部已读
 *   <li><b>撤回</b>：{@code POST /{id}/recall} 撤回通知（仅发送者 / 管理员可操作）
 *   <li><b>实时推送</b>：{@code POST /push} 通过 WebSocket 实时推送到客户端
 *   <li><b>详情</b>：{@code GET /{id}} 通知详情（含回执状态、阅读时间）
 * </ul>
 *
 * <p><b>与 MessageController 的区别：</b>
 *
 * <ul>
 *   <li>本 Controller：<b>站内通知</b>（DB 持久化 + WebSocket 推送），收件人必须在系统内有账号
 *   <li>MessageController：<b>多渠道发送</b>（短信 / 邮件 / IM / WebSocket），收件人是渠道账号
 * </ul>
 *
 * <p><b>实时推送：</b>站内通知创建后由 {@link RealtimePushService} 通过 WebSocket 推送到在线用户的浏览器， 离线用户登录后从 {@code
 * /page} 拉取未读通知。{@code ydsz:msg:realtime:user:{userId}} 用于维护在线用户连接。
 *
 * <p><b>多租户隔离：</b>所有操作按 {@code tenantId} 隔离，跨租户通知不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>撤回操作仅发送者 / 管理员可执行（由 Service 层校验）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_MESSAGE_SEND} 等权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.core.NotificationService 站内通知服务
 * @see com.njydsz.message.server.realtime.RealtimePushService 实时推送服务
 * @see com.njydsz.message.server.service.receipt.RecallService 撤回服务
 */
@Tag(name = "站内通知", description = "站内通知发送/收件箱/已读/撤回/推送")
@Slf4j
@RequestMapping("/api/v1/message/notifications")
@RequiredArgsConstructor
public class NotificationController {

  /** 站内通知服务 */
  private final NotificationService notificationService;

  /** 消息撤回服务 */
  private final RecallService recallService;

  /** 实时推送服务（WebSocket） */
  private final RealtimePushService realtimePushService;

  /**
   * 发送站内通知。
   *
   * @param dto 通知发送请求体
   * @return 统一响应结果，包含发送条数
   */
  @Operation(summary = "发送站内通知", description = "发送单条站内通知。通知持久化到数据库后写入收件人收件箱，并经由 WebSocket 实时推送到在线用户。返回成功发送的条数。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
  @Idempotent(key = "ydsz:message:notification:send", ttlSeconds = 5)
  @Audit(
      module = "通知管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'send'")
  @RateLimit(resource = "message.notification.send", threshold = 50)
  @PostMapping("/send")
  public YdszResponse<Integer> send(@Valid @RequestBody NotificationSendDTO dto) {
    return YdszResponse.success(notificationService.send(dto));
  }

  /**
   * 分页查询当前用户收件箱。
   *
   * @param query 查询参数
   * @return 通知分页结果
   */
  @Operation(summary = "收件箱分页", description = "分页查询当前登录用户的站内通知列表。支持按已读状态、通知类型、关键词、时间范围过滤。返回分页结果含 MsgNotificationVO（通知 ID、标题、内容、类型、已读状态、发送时间）。")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
  @GetMapping("/inbox")
  public YdszResponse<PageResponse<List<MsgNotificationVO>>> inbox(NotificationQueryDTO query) {
    Page<MsgNotification> page = notificationService.inbox(AuthContextUtils.getUserId(), query);
    return YdszResponse.success(PageResponses.success(page, MessageConverter.INSTANT::entityToVO));
  }

  /**
   * 查询当前用户未读通知数量。
   *
   * @return 统一响应结果，包含未读数量
   */
  @Operation(summary = "未读数量", description = "查询当前登录用户未读站内通知总数。用于导航栏徽标角标展示。无需参数，按 tenantId + userId 隔离。返回未读数量。")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
  @GetMapping("/unreadCount")
  public YdszResponse<Long> countUnread() {
    return YdszResponse.success(notificationService.countUnread(AuthContextUtils.getUserId()));
  }

  /**
   * 标记单条通知为已读。
   *
   * @param id 通知 ID
   * @return 统一响应结果，true 表示标记成功
   */
  @Operation(summary = "标记单条已读", description = "将指定通知标记为已读状态。通过路径参数 id 指定通知 ID，仅操作当前用户自己的通知。返回 true 表示标记成功，false 表示通知不存在或无权限。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
  @Idempotent(key = "ydsz:message:notification:markRead", ttlSeconds = 5)
  @Audit(
      module = "通知管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'markRead'")
  @RateLimit(resource = "message.notification.markRead", threshold = 50)
  @PostMapping("/{id}/read")
  public YdszResponse<Boolean> markRead(@PathVariable String id) {
    return YdszResponse.success(notificationService.markRead(AuthContextUtils.getUserId(), id));
  }

  /**
   * 将当前用户全部通知标记为已读。
   *
   * @return 统一响应结果，包含已标记条数
   */
  @Operation(summary = "全部标记已读", description = "将当前登录用户所有未读通知批量标记为已读。无需参数，按 tenantId + userId 隔离。返回本次成功标记的条数。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
  @Idempotent(key = "ydsz:message:notification:markAllRead", ttlSeconds = 5)
  @Audit(
      module = "通知管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'markAllRead'")
  @RateLimit(resource = "message.notification.markAllRead", threshold = 50)
  @PostMapping("/readAll")
  public YdszResponse<Integer> markAllRead() {
    return YdszResponse.success(notificationService.markAllRead(AuthContextUtils.getUserId()));
  }

  /**
   * 删除通知（仅删当前用户自己的）。
   *
   * @param ids 通知 ID 列表
   * @return 统一响应结果
   */
  @Operation(summary = "删除通知(仅删自己的)", description = "批量删除当前登录用户的站内通知。仅能删除属于自己的通知，跨租户/跨用户操作被拒绝。请求体为通知 ID 列表。返回 Void（无业务数据）。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_DELETE)
  @Idempotent(key = "ydsz:message:NotificationController:delete:lock", ttlSeconds = 5)
  @Audit(
      module = "通知管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'delete'")
  @RateLimit(resource = "message.notification.delete", threshold = 50)
  @DeleteMapping
  public YdszResponse<Void> delete(@Valid @RequestBody List<String> ids) {
    notificationService.delete(AuthContextUtils.getUserId(), ids);
    return YdszResponse.success();
  }

  /**
   * 撤回通知。
   *
   * @param id 通知 ID
   * @return 统一响应结果，true 表示撤回成功
   */
  @Operation(summary = "撤回通知", description = "撤回已发送的站内通知。仅发送者本人或管理员可执行撤回操作（Service 层校验权限）。通过路径参数 id 指定通知 ID。返回 true 表示撤回成功，false 表示通知不存在或无权限。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_RECALL)
  @Idempotent(key = "ydsz:message:NotificationController:recall:lock", ttlSeconds = 5)
  @Audit(
      module = "通知管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'recall'")
  @RateLimit(resource = "message.notification.recall", threshold = 50)
  @PostMapping("/{id}/recall")
  public YdszResponse<Boolean> recall(@PathVariable String id) {
    return YdszResponse.success(recallService.recallNotification(AuthContextUtils.getUserId(), id));
  }

  /**
   * 单推（实时推送至指定用户）。
   *
   * @param userId 目标用户 ID
   * @param type 推送类型
   * @param payload 推送数据
   * @return 统一响应结果，包含推送结果信息
   */
  @Operation(summary = "单推(实时推送指定用户)", description = "通过 WebSocket 向指定用户实时推送消息。参数通过 Query String 传入 userId、type 和请求体 PushRealtimeRequestDTO（含业务数据 data）。推送成功返回 success=true 及目标 userId 和 type。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_PUSH)
  @Idempotent(key = "ydsz:message:NotificationController:push:lock", ttlSeconds = 5)
  @Audit(
      module = "通知管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'postmapping'")
  @PostMapping("/push")
  public YdszResponse<Map<String, Object>> push(
      @RequestParam String userId,
      @RequestParam String type,
      @Valid @RequestBody PushRealtimeRequestDTO payload) {
    Object bizData = payload != null ? payload.getData() : null;
    realtimePushService.pushToUser(userId, type, bizData);
    return YdszResponse.success(Map.of("success", true, "userId", userId, "type", type));
  }

  /**
   * 广播（实时推送至所有在线用户）。
   *
   * <p>P0-3-fix：请求体使用 {@link BroadcastRequestDTO}，将 topic 并入 body， 返回 {@link MessageResult}
   * 使调用方可感知推送结果。
   *
   * @param request 广播请求（topic、data、可选 messageId）
   * @return 统一响应结果，包含 traceId 用于链路追踪
   */
  @Operation(summary = "广播(实时推送所有在线用户)", description = "通过 WebSocket 向所有在线用户实时广播消息。请求体 BroadcastRequestDTO 含 topic（主题）、data（业务数据）、可选 messageId。返回 MessageResult 含 topic 和 traceId 用于链路追踪。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_BROADCAST)
  @Idempotent(key = "ydsz:message:NotificationController:broadcast:lock", ttlSeconds = 5)
  @Audit(
      module = "通知管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'postmapping'")
  @PostMapping("/broadcast")
  public YdszResponse<MessageResult> broadcast(@Valid @RequestBody BroadcastRequestDTO request) {
    realtimePushService.broadcast(request.getTopic(), request.getData());
    String traceId = WebSocketTraceContext.getTraceId();
    return YdszResponse.success(MessageResult.ok(request.getTopic(), traceId));
  }

  /**
   * 单播实时推送（供 Feign 远程调用）。
   *
   * <p>P0-3-fix：新增端点，支持工作流、定时任务等模块通过 Feign 单播推送。
   *
   * @param request 单播请求（userId、type、data、可选 messageId）
   * @return 统一响应结果，包含 traceId 用于链路追踪
   */
  @Operation(summary = "单播实时推送(Feign远程调用)", description = "供工作流、定时任务等模块通过 Feign 远程调用的单播推送端点。请求体 PushRealtimeRequestDTO 含 userId、type、data、可选 messageId。返回 MessageResult 含 type 和 traceId 用于链路追踪。")
  @ApiResponse(responseCode = "200", description = "操作成功")
  @ApiResponse(responseCode = "400", description = "请求参数错误")
  @ApiResponse(responseCode = "429", description = "请求过于频繁")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_PUSH)
  @Idempotent(key = "ydsz:message:NotificationController:push-realtime:lock", ttlSeconds = 5)
  @PostMapping("/push-realtime")
  public YdszResponse<MessageResult> pushRealtime(
      @Valid @RequestBody PushRealtimeRequestDTO request) {
    realtimePushService.pushToUser(request.getUserId(), request.getType(), request.getData());
    String traceId = WebSocketTraceContext.getTraceId();
    return YdszResponse.success(MessageResult.ok(request.getType(), traceId));
  }
}
