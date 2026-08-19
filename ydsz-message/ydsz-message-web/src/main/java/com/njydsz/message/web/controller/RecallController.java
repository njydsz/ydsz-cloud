package com.njydsz.message.web.controller.receipt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.dto.receipt.RecallRequestDTO;
import com.njydsz.message.server.service.receipt.RecallService;

/**
 * 消息撤回（Recall）Controller。
 *
 * <p>提供<b>已发送消息 / 站内通知的撤回</b>能力。 撤回语义：在用户尚未阅读时收回消息，已读消息撤回后仍会展示撤回提示但不再跳转原内容。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/recall/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>撤回站内通知</b>：{@code POST /notification} — 撤回指定通知，仅发送者 / 管理员可操作
 *   <li><b>按日志 ID 撤回消息</b>：{@code POST /message/{logId}} — 撤回单条已发消息
 *   <li><b>按 msgId 撤回消息</b>：{@code POST /msg/{msgId}} — 支持撤回时间窗口校验（默认 30 分钟）
 *   <li><b>批量撤回</b>：{@code POST /batch} — 按 (bizType, bizId) 撤回某单据的全部通知
 * </ul>
 *
 * <p><b>撤回规则：</b>
 *
 * <ul>
 *   <li><b>站内通知</b>：未读可彻底删除；已读改为「撤回」状态展示
 *   <li><b>已发送消息</b>：可撤回前提是通道支持（如钉钉/企业微信支持 IM 消息撤回，短信/邮件不可撤回已发出内容）
 *   <li><b>时间窗口</b>：撤回受 {@code ydsz.message.recall-window-minutes}（默认 30 min）限制，超时不允许撤回
 *   <li><b>权限</b>：仅发送者本人 / 管理员可操作，服务层校验
 * </ul>
 *
 * <p><b>与 NotificationController.recall 的区别：</b>
 *
 * <ul>
 *   <li>NotificationController：<b>站内通知</b>专用撤回，自动取当前登录用户
 *   <li>本 Controller：<b>消息+通知</b>通用撤回，userId 显式传入，支持任意用户代为撤回（管理员场景）
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有操作按 {@code tenantId} 隔离，跨租户撤回不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>所有写接口启用 {@link Idempotent} 5s 防重
 *   <li>所有写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>所有写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_RECALL_ACT} 权限码
 *   <li>撤回操作的归属校验（仅发送者/管理员）由 Service 层执行，本 Controller 仅做参数透传
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.receipt.RecallService 消息撤回服务
 * @see com.njydsz.message.domain.dto.receipt.RecallRequestDTO 撤回请求 DTO
 */
@Tag(name = "消息撤回", description = "通知/消息撤回")
@RestController
@RequestMapping("/api/v1/message/recall")
@RequiredArgsConstructor
public class RecallController {

  /** 消息撤回服务 */
  private final RecallService recallService;

  /**
   * 撤回站内通知。
   *
   * @param userId 用户 ID
   * @param dto 撤回请求体（含通知 ID）
   * @return 统一响应结果，true 表示撤回成功
   */
  @Operation(summary = "撤回站内通知")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECALL_ACT)
  @Idempotent(key = "ydsz:message:RecallController:recallNotification:lock", ttlSeconds = 5)
  @Audit(
      module = "消息撤回",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'recallNotification'")
  @RateLimit(resource = "message.recall.recallNotification", threshold = 50)
  @PostMapping("/notification")
  public YdszResponse<Boolean> recallNotification(
      @RequestParam String userId, @Valid @RequestBody RecallRequestDTO dto) {
    return YdszResponse.success(recallService.recallNotification(userId, dto.getId()));
  }

  /**
   * 撤回已发送消息。
   *
   * @param logId 发送日志 ID
   * @return 统一响应结果，true 表示撤回成功
   */
  @Operation(summary = "撤回已发送消息")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECALL_ACT)
  @Idempotent(key = "ydsz:message:RecallController:recallMessage:lock", ttlSeconds = 5)
  @Audit(
      module = "消息撤回",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'recallMessage'")
  @RateLimit(resource = "message.recall.recallMessage", threshold = 50)
  @PostMapping("/message/{logId}")
  public YdszResponse<Boolean> recallMessage(@PathVariable String logId) {
    return YdszResponse.success(recallService.recallMessage(logId));
  }

  /**
   * P0-4: 按 msgId 撤回已发送消息。
   *
   * <p>支持撤回时间窗口校验（默认 30 分钟内可撤回）。
   *
   * @param msgId 消息 ID
   * @return 撤回结果
   */
  @Operation(summary = "按消息 ID 撤回消息")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECALL_ACT)
  @Idempotent(key = "ydsz:message:RecallController:recallByMsgId:lock", ttlSeconds = 5)
  @Audit(
      module = "消息撤回",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'recallByMsgId'")
  @RateLimit(resource = "message.recall.recallByMsgId", threshold = 50)
  @PostMapping("/msg/{msgId}")
  public YdszResponse<Boolean> recallByMsgId(@PathVariable String msgId) {
    return YdszResponse.success(recallService.recallByMsgId(msgId));
  }

  /**
   * 按业务类型和单据 ID 批量撤回消息。
   *
   * @param dto 批量撤回请求体（含 bizType + bizId）
   * @return 统一响应结果，包含撤回条数
   */
  @Operation(summary = "按业务类型+单据 ID 批量撤回")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECALL_ACT)
  @Idempotent(key = "ydsz:message:RecallController:recallBatch:lock", ttlSeconds = 5)
  @Audit(
      module = "消息撤回",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'recallBatch'")
  @RateLimit(resource = "message.recall.recallBatch", threshold = 50)
  @PostMapping("/batch")
  public YdszResponse<Integer> recallBatch(@Valid @RequestBody RecallRequestDTO dto) {
    return YdszResponse.success(recallService.recallBatch(dto.getBizType(), dto.getBizId()));
  }
}
