package com.njydsz.message.web.controller.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.core.MessageLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 死信（Dead Letter）管理 Controller。
 *
 * <p>提供<b>消息死信查询与人工干预</b>的 HTTP API。 死信指超过最大重试次数（默认 {@code ydsz.message.max-retry-count}，通常 5
 * 次）仍发送失败的消息， 由 {@code RetryScheduler} 调度器在每次重试失败后递增 {@code retryCount}， 超过阈值后将 {@code
 * ydsz_msg_log.status} 置为 {@code DEAD}，进入死信状态。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/deadLetter/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>分页查询死信</b>：{@code GET /page} — 强制过滤 {@code status=DEAD}，按通道 / 业务类型 / 接收人 / 租户等多维过滤
 *   <li><b>手动重发</b>：{@code POST /{logId}/resend} — 仅 {@code DEAD} 状态可触发，重置 {@code
 *       retryCount/errorMessage/nextRetryAt} 后立即重新投递
 * </ul>
 *
 * <p><b>死信状态机：</b>消息生命周期中可能进入死信的状态节点：
 *
 * <ol>
 *   <li>{@code PENDING}（待发）→ {@code SENDING}（发送中）
 *   <li>{@code SENDING} → {@code RETRY}（重试中，{@code retryCount < maxRetry}）
 *   <li>{@code RETRY} → {@code DEAD}（死信，{@code retryCount ≥ maxRetry}）
 *   <li>人工干预：{@code DEAD} → {@code PENDING}（重发成功后转为正常发送流）
 * </ol>
 *
 * <p><b>重发行为：</b>{@code /resend} 成功后可能产生两种结果：
 *
 * <ul>
 *   <li>立即成功 → 状态变为 {@code SUCCESS}
 *   <li>再次失败 → 状态回退到 {@code RETRY}，进入正常重试调度（不会立刻再次变 {@code DEAD}）
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有查询按 {@code tenantId} 过滤，跨租户死信不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（resend）启用 {@link Idempotent} 5s 防重，避免运维误操作重复触发重发
 *   <li>写接口（resend）启用 {@link RateLimit} 50 QPS 限流
 *   <li>写接口（resend）启用 {@link Audit} 审计日志（异步持久化）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_DEAD_LETTER_RESEND}
 *       权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.core.MessageLogService 消息日志服务
 * @see com.njydsz.message.domain.entity.core.MsgLog 发送日志实体
 * @see com.njydsz.message.domain.enums.core.MessageStatusEnum 消息状态枚举
 */
@Slf4j
@Tag(name = "死信管理", description = "死信查询与手动重发")
@RestController
@RequestMapping("/api/v1/message/deadLetter")
@RequiredArgsConstructor
public class DeadLetterController {

  /** 消息日志服务 */
  private final MessageLogService messageLogService;

  /**
   * 分页查询死信列表。
   *
   * <p>强制 {@code status=DEAD},支持按通道 / 业务类型 / 接收人 / 租户等过滤。
   *
   * @param query 查询参数（status 字段被忽略,固定为 DEAD）
   * @return 死信分页
   */
  @Operation(summary = "分页查询死信列表")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_DEAD_LETTER_VIEW)
  @GetMapping("/page")
  public PageResponse<List<MsgLogVO>> page(MessageLogQueryDTO query) {
    if (query == null) {
      query = new MessageLogQueryDTO();
    }
    query.setStatus(MessageStatusEnum.DEAD.name());
    Page<MsgLog> page = messageLogService.page(query);
    return PageResponses.success(page, MessageConverter.INSTANT::entityToVO);
  }

  /**
   * 手动重发死信。
   *
   * <p>仅 DEAD 状态可重发。重置 retryCount / errorMessage / nextRetryAt 后立即重新投递, 投递成功 → SUCCESS,投递失败 →
   * RETRY（进入正常重试调度）。
   *
   * @param logId 死信日志 ID
   * @return 操作结果
   */
  @Operation(summary = "手动重发死信")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_DEAD_LETTER_RESEND)
  @Idempotent(key = "ydsz:message:DeadLetterController:resend:lock", ttlSeconds = 5)
  @Audit(
      module = "死信管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'resend'")
  @RateLimit(resource = "message.deadletter.resend", threshold = 50)
  @PostMapping("/{logId}/resend")
  public BaseResponse<Void> resend(@PathVariable String logId) {
    if (logId == null || logId.isBlank()) {
      return BaseResponse.error(BaseResultCode.BAD_REQUEST, "死信日志 ID 不能为空");
    }
    messageLogService.resendDead(logId);
    return BaseResponse.success();
  }
}
