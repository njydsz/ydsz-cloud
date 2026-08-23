package com.njydsz.message.web.controller.receipt;

import java.util.List;

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

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.dto.ReceiptCallbackDTO;
import com.njydsz.message.domain.entity.receipt.MsgReceipt;
import com.njydsz.message.server.service.receipt.ReceiptService;

/**
 * 消息回执（Receipt）Controller。
 *
 * <p>提供<b>三方短信/邮件服务商回执回调</b>与<b>回执查询</b>的 HTTP API。 三方服务商（阿里云 / 腾讯云 / 华为云 / 飞书 / 钉钉 /
 * 企业微信）在送达、阅读、点击等节点 会主动回调 {@code /callback}，本 Controller 将其转化为标准化回执记录写入 {@code ydsz_msg_receipt}，
 * 并更新 {@code ydsz_msg_log.receiptStatus}。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/receipt/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>回执回调</b>：{@code POST /callback} — 三方服务商主动回调入口，幂等接收（同一 providerMsgId 多次回调只生效一次）
 *   <li><b>回执查询</b>：{@code GET /{logId}} — 按发送日志 ID 查询该条消息的全部回执记录（含 DELIVERED / READ / CLICKED /
 *       FAILED 等）
 * </ul>
 *
 * <p><b>回执回调签名校验：</b>不同服务商的签名机制不同（阿里云 HMAC-SHA1 / 腾讯云 HMAC-SHA256 / 飞书 Token 等），由 {@code
 * ReceiptService.callback} 内部根据 {@code dto.channel} 路由到对应的验签器。
 *
 * <p><b>回执状态机：</b>{@code NONE → DELIVERED → READ → CLICKED}，任意节点可跳变为 {@code FAILED}。 状态机转换在 {@code
 * ReceiptService} 中校验，确保不会回退。
 *
 * <p><b>与 ReadReceiptController 的区别：</b>
 *
 * <ul>
 *   <li>本 Controller：<b>三方主动回调</b>，由服务商 HTTP POST 触发，需做签名校验
 *   <li>ReadReceiptController：<b>我方主动探测</b>，由我方在邮件/SMS 中植入追踪像素/短链被动触发
 * </ul>
 *
 * <p><b>多租户隔离：</b>回执按 {@code ydsz_msg_log.tenantId} 自动继承，无需显式传参。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（callback）启用 {@link Idempotent} 5s 防重，相同 providerMsgId 重复回调幂等
 *   <li>写接口（callback）启用 {@link RateLimit} 50 QPS 限流，防止恶意刷接口
 *   <li>写接口（callback）启用 {@link Audit} 审计日志（异步持久化）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_RECEIPT_CALLBACK} 权限码
 *   <li>建议在网关层对 {@code /callback} 配置 IP 白名单，仅允许三方服务商出口 IP 访问
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.receipt.ReceiptService 消息回执服务
 * @see com.njydsz.message.domain.entity.receipt.MsgReceipt 回执实体
 * @see ReceiptCallbackDTO 回执回调 DTO
 */
@Tag(name = "消息回执", description = "服务商回执回调与查询")
@Slf4j
@RequestMapping("/api/v1/message/receipt")
@RequiredArgsConstructor
public class ReceiptController {

  /** 消息回执服务 */
  private final ReceiptService receiptService;

  /**
   * 服务商回执回调接口。
   *
   * @param dto 回执回调请求体
   * @return 统一响应结果
   */
  @Operation(summary = "回执回调")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECEIPT_CALLBACK)
  @Idempotent(key = "ydsz:message:ReceiptController:callback:lock", ttlSeconds = 5)
  @Audit(
      module = "消息回执",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'callback'")
  @RateLimit(resource = "message.receipt.callback", threshold = 50)
  @PostMapping("/callback")
  public YdszResponse<Void> callback(@Valid @RequestBody ReceiptCallbackDTO dto) {
    receiptService.callback(dto);
    return YdszResponse.success();
  }

  /**
   * 按发送日志 ID 查询回执列表。
   *
   * @param logId 发送日志 ID
   * @return 统一响应结果，包含回执列表
   */
  @Operation(summary = "按日志 ID 查询回执列表")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_RECEIPT_VIEW)
  @GetMapping("/{logId}")
  public YdszResponse<List<MsgReceipt>> listByLogId(@PathVariable String logId) {
    return YdszResponse.success(receiptService.listByLogId(logId));
  }
}
