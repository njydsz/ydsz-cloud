package com.njydsz.message.web.controller.receipt;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
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

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.server.service.receipt.ReadStatusSyncService;

/**
 * 消息已读状态同步（Read Status）Controller。
 *
 * <p>提供<b>全通道消息已读/未读状态管理</b>的 HTTP API，是 P1-3「统一已读状态」的核心入口。 不同通道（站内信 / 短信 / 邮件 / 钉钉 / 飞书 /
 * 企业微信）的已读状态由本 Controller 统一管理， 用户在一个渠道的已读操作会同步到所有渠道的未读计数。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/read-status/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>标记消息已读</b>：{@code POST /read/{msgId}} — 标记某条消息已读
 *   <li><b>批量已读</b>：{@code POST /readBatch} — 一次性标记多条消息已读
 *   <li><b>通知已读</b>：{@code POST /notification/{notificationId}} — 标记某条站内通知已读
 *   <li><b>全部已读</b>：{@code POST /notification/readAll} — 标记某用户某业务类型下全部通知已读
 *   <li><b>未读数量</b>：{@code GET /unreadCount} — 查询用户的未读数量（支持按通道过滤）
 * </ul>
 *
 * <p><b>与 ReadReceiptController 的区别：</b>
 *
 * <ul>
 *   <li>本 Controller：<b>有登录态</b>，由用户在前端主动操作（点击「标记已读」「全部已读」）
 *   <li>ReadReceiptController：<b>无登录态</b>，由邮件追踪像素 / 短信短链被动触发
 * </ul>
 *
 * <p><b>未读计数缓存：</b>未读数量通过 Redis 缓存（{@code ydsz:msg:unread:count:{userId}}）避免每次实时统计，
 * 已读操作时同步失效缓存，保证一致性。
 *
 * <p><b>多租户隔离：</b>所有已读状态按 {@code tenantId} 隔离，跨租户状态不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（mark 系列）启用 {@link Idempotent} 5s 防重
 *   <li>写接口（mark 系列）启用 {@link RateLimit} 50 QPS 限流
 *   <li>写接口（mark 系列）启用 {@link Audit} 审计日志（异步持久化）
 *   <li>读接口（unreadCount）需校验 {@link PermissionCodes#NOTIF_MESSAGE_VIEW} 权限码
 *   <li>用户仅能标记自己的消息（userId 与登录态一致性校验）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.receipt.ReadStatusSyncService 已读状态同步服务
 */
@Tag(name = "已读状态", description = "消息已读/未读状态同步")
@Slf4j
@RequestMapping("/api/v1/message/read-status")
@RequiredArgsConstructor
public class ReadStatusController {

  /** 已读状态同步服务 */
  private final ReadStatusSyncService readStatusSyncService;

  /**
   * 标记消息为已读。
   *
   * @param msgId 消息 ID
   * @param userId 用户 ID
   * @return 统一响应结果，true 表示标记成功
   */
  @Operation(summary = "标记消息已读")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
  @Idempotent(key = "ydsz:message:ReadStatusController:markRead:lock", ttlSeconds = 5)
  @Audit(
      module = "已读状态",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'markRead'")
  @RateLimit(resource = "message.readstatus.markRead", threshold = 50)
  @PostMapping("/read/{msgId}")
  public YdszResponse<Boolean> markRead(@PathVariable String msgId, @RequestParam String userId) {
    return YdszResponse.success(readStatusSyncService.markRead(msgId, userId));
  }

  /**
   * 批量标记消息为已读。
   *
   * @param msgIds 消息 ID 列表
   * @param userId 用户 ID
   * @return 统一响应结果，包含已标记条数
   */
  @Operation(summary = "批量标记消息已读")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
  @Idempotent(key = "ydsz:message:ReadStatusController:markReadBatch:lock", ttlSeconds = 5)
  @Audit(
      module = "已读状态",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'markReadBatch'")
  @RateLimit(resource = "message.readstatus.markReadBatch", threshold = 50)
  @PostMapping("/readBatch")
  public YdszResponse<Integer> markReadBatch(
      @Valid @RequestBody List<String> msgIds, @RequestParam String userId) {
    return YdszResponse.success(readStatusSyncService.markReadBatch(msgIds, userId));
  }

  /**
   * 标记站内通知为已读。
   *
   * @param notificationId 通知 ID
   * @param userId 用户 ID
   * @return 统一响应结果，true 表示标记成功
   */
  @Operation(summary = "标记站内通知已读")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
  @Idempotent(key = "ydsz:message:ReadStatusController:markNotificationRead:lock", ttlSeconds = 5)
  @Audit(
      module = "已读状态",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'markNotificationRead'")
  @RateLimit(resource = "message.readstatus.markNotificationRead", threshold = 50)
  @PostMapping("/notification/{notificationId}")
  public YdszResponse<Boolean> markNotificationRead(
      @PathVariable String notificationId, @RequestParam String userId) {
    return YdszResponse.success(readStatusSyncService.markNotificationRead(notificationId, userId));
  }

  /**
   * 将用户全部通知标记为已读。
   *
   * @param userId 用户 ID
   * @param bizType 业务类型过滤（可选）
   * @return 统一响应结果，包含已标记条数
   */
  @Operation(summary = "全部通知标记已读")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
  @Idempotent(
      key = "ydsz:message:ReadStatusController:markAllNotificationsRead:lock",
      ttlSeconds = 5)
  @Audit(
      module = "已读状态",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'markAllNotificationsRead'")
  @PostMapping("/notification/readAll")
  public YdszResponse<Integer> markAllNotificationsRead(
      @RequestParam String userId, @RequestParam(required = false) String bizType) {
    return YdszResponse.success(readStatusSyncService.markAllNotificationsRead(userId, bizType));
  }

  /**
   * 查询用户未读消息数量。
   *
   * @param userId 用户 ID
   * @param channel 通道过滤（可选）
   * @return 统一响应结果，包含 total 和 byChannel 两个未读计数
   */
  @Operation(summary = "查询用户未读消息数量")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
  @GetMapping("/unreadCount")
  public YdszResponse<Map<String, Long>> getUnreadCount(
      @RequestParam String userId, @RequestParam(required = false) String channel) {
    long total = readStatusSyncService.getUnreadCount(userId);
    long byChannel =
        channel != null ? readStatusSyncService.getUnreadCountByChannel(userId, channel) : total;
    return YdszResponse.success(Map.of("total", total, "byChannel", byChannel));
  }
}
