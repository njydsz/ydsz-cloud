package com.njydsz.message.domain.enums;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 消息中心模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}， 支持
 * i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 *
 * <ul>
 *   <li>B91001-B91099 模板
 *   <li>B91101-B91199 通知/消息日志
 *   <li>B91201-B91299 渠道/路由
 *   <li>B91301-B91399 批量/灰度
 *   <li>B91401-B91499 退订/偏好/反馈
 *   <li>B91501-B91599 发送管线（限流/去重/DND/抑制/配额）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "message", description = "消息中心")
public enum MessageExceptionCode implements ExceptionCode {

  // ==================== B91001-B91099 模板 ====================
  TEMPLATE_NOT_FOUND("B91001", "message.template.not.found", 404),
  TEMPLATE_CODE_DUPLICATE("B91002", "message.template.code.duplicate"),
  TEMPLATE_AUDIT_PENDING("B91003", "message.template.audit.pending"),
  TEMPLATE_AUDIT_REJECTED("B91004", "message.template.audit.rejected"),
  TEMPLATE_VARIABLE_MISSING("B91005", "message.template.variable.missing"),

  // ==================== B91101-B91199 通知/消息日志 ====================
  NOTIFICATION_NOT_FOUND("B91101", "message.notification.not.found", 404),
  MESSAGE_LOG_NOT_FOUND("B91102", "message.LOG.not.found", 404),
  MESSAGE_SEND_FAILED("B91103", "message.send.failed", 500),
  MESSAGE_RECALL_FAILED("B91104", "message.recall.failed"),

  // ==================== B91201-B91299 渠道/路由 ====================
  CHANNEL_NOT_CONFIGURED("B91201", "message.channel.not.configured"),
  CHANNEL_SEND_FAILED("B91202", "message.channel.send.failed", 500),
  ROUTE_RULE_NOT_FOUND("B91203", "message.route.rule.not.found", 404),
  CHANNEL_BLOCKED("B91204", "message.channel.blocked"),

  // ==================== B91301-B91399 批量/灰度 ====================
  BATCH_NOT_FOUND("B91301", "message.batch.not.found", 404),
  BATCH_ALREADY_RUNNING("B91302", "message.batch.already.running"),
  CANARY_NOT_FOUND("B91303", "message.canary.not.found", 404),

  // ==================== B91401-B91499 退订/偏好/反馈 ====================
  UNSUBSCRIBE_TOKEN_INVALID("B91401", "message.unsubscribe.token.invalid"),
  PREFERENCE_NOT_FOUND("B91402", "message.preference.not.found", 404),
  FEEDBACK_NOT_FOUND("B91403", "message.feedback.not.found", 404),

  // ==================== B91501-B91599 发送管线（限流/去重/DND/抑制/配额） ====================
  /** 通道未启用 */
  CHANNEL_NOT_ENABLED("B91501", "message.channel.not.enabled"),
  /** 发送限流（通道级 QPS 超限） */
  SEND_RATE_LIMITED("B91502", "message.send.rate.limit", 429),
  /** 多维度限流（receiver/template/tenant 超限） */
  SEND_DIMENSION_LIMITED("B91503", "message.send.dimension.limit", 429),
  /** 发送频率超限（用户级频率限制） */
  SEND_FREQUENCY_LIMITED("B91504", "message.send.frequency.limit", 429),
  /** 发送方配额已用尽 */
  SEND_QUOTA_EXHAUSTED("B91505", "message.send.quota.exhausted", 429),
  /** 用户已退订该消息主题 */
  USER_UNSUBSCRIBED("B91506", "message.user.unsubscribed"),
  /** 当前为免打扰时段 */
  DND_PERIOD_ACTIVE("B91507", "message.dnd.period.active"),
  /** DND 延迟超过最大阈值 */
  DND_DEFER_EXCEED("B91508", "message.dnd.defer.exceed"),
  /** 消息重复（已被去重） */
  MESSAGE_DUPLICATED("B91509", "message.duplicated"),
  /** 跨渠道抑制 */
  CHANNEL_SUPPRESSED("B91510", "message.channel.suppressed");

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  /** 是否可恢复（客户端是否应重试） */
  private final boolean retryable;

  /** 建议重试等待秒数 */
  private final int retryAfterSeconds;

  MessageExceptionCode(String code, String key) {
    this(code, key, 400, false, 0);
  }

  MessageExceptionCode(String code, String key, int httpStatus) {
    this(code, key, httpStatus, httpStatus == 429, 0);
  }

  MessageExceptionCode(String code, String key, int httpStatus, boolean retryable, int retryAfterSeconds) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
    this.retryable = retryable;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  @Override
  public boolean retryable() {
    return retryable;
  }

  @Override
  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
