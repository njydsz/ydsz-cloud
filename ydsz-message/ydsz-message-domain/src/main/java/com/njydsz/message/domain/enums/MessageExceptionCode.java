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
 * @since 26.09.01
 */
@Getter
@YdszExceptionCode(module = "message", description = "消息中心")
public enum MessageExceptionCode implements ExceptionCode {

  // ==================== B91001-B91099 模板 ====================
  /** Template not found */
  TEMPLATE_NOT_FOUND("B91001", "message.template.not.found", 404),
  /** Template code duplicate */
  TEMPLATE_CODE_DUPLICATE("B91002", "message.template.code.duplicate"),
  /** Template audit pending */
  TEMPLATE_AUDIT_PENDING("B91003", "message.template.audit.pending"),
  /** Template audit rejected */
  TEMPLATE_AUDIT_REJECTED("B91004", "message.template.audit.rejected"),
  /** Template variable missing */
  TEMPLATE_VARIABLE_MISSING("B91005", "message.template.variable.missing"),

  // ==================== B91101-B91199 通知/消息日志 ====================
  /** Notification not found */
  NOTIFICATION_NOT_FOUND("B91101", "message.notification.not.found", 404),
  /** Message log not found */
  MESSAGE_LOG_NOT_FOUND("B91102", "message.LOG.not.found", 404),
  /** Message send failed */
  MESSAGE_SEND_FAILED("B91103", "message.send.failed", 500),
  /** Message recall failed */
  MESSAGE_RECALL_FAILED("B91104", "message.recall.failed"),

  // ==================== B91201-B91299 渠道/路由 ====================
  /** Channel not configured */
  CHANNEL_NOT_CONFIGURED("B91201", "message.channel.not.configured"),
  /** Channel send failed */
  CHANNEL_SEND_FAILED("B91202", "message.channel.send.failed", 500),
  /** Route rule not found */
  ROUTE_RULE_NOT_FOUND("B91203", "message.route.rule.not.found", 404),
  /** Channel blocked */
  CHANNEL_BLOCKED("B91204", "message.channel.blocked"),

  // ==================== B91301-B91399 批量/灰度 ====================
  /** Batch not found */
  BATCH_NOT_FOUND("B91301", "message.batch.not.found", 404),
  /** Batch already running */
  BATCH_ALREADY_RUNNING("B91302", "message.batch.already.running"),
  /** Canary not found */
  CANARY_NOT_FOUND("B91303", "message.canary.not.found", 404),

  // ==================== B91401-B91499 退订/偏好/反馈 ====================
  /** Unsubscribe token invalid */
  UNSUBSCRIBE_TOKEN_INVALID("B91401", "message.unsubscribe.token.invalid"),
  /** Preference not found */
  PREFERENCE_NOT_FOUND("B91402", "message.preference.not.found", 404),
  /** Feedback not found */
  FEEDBACK_NOT_FOUND("B91403", "message.feedback.not.found", 404),

  // ==================== B91501-B91599 发送管线（限流/去重/DND/抑制/配额） ====================
  /** 通道未启用 */
  /** Channel not enabled */
  CHANNEL_NOT_ENABLED("B91501", "message.channel.not.enabled"),
  /** 发送限流（通道级 QPS 超限） */
  /** Send rate limited */
  SEND_RATE_LIMITED("B91502", "message.send.rate.limit", 429),
  /** 多维度限流（receiver/template/tenant 超限） */
  /** Send dimension limited */
  SEND_DIMENSION_LIMITED("B91503", "message.send.dimension.limit", 429),
  /** 发送频率超限（用户级频率限制） */
  /** Send frequency limited */
  SEND_FREQUENCY_LIMITED("B91504", "message.send.frequency.limit", 429),
  /** 发送方配额已用尽 */
  /** Send quota exhausted */
  SEND_QUOTA_EXHAUSTED("B91505", "message.send.quota.exhausted", 429),
  /** 用户已退订该消息主题 */
  /** User unsubscribed */
  USER_UNSUBSCRIBED("B91506", "message.user.unsubscribed"),
  /** 当前为免打扰时段 */
  /** Dnd period active */
  DND_PERIOD_ACTIVE("B91507", "message.dnd.period.active"),
  /** DND 延迟超过最大阈值 */
  /** Dnd defer exceed */
  DND_DEFER_EXCEED("B91508", "message.dnd.defer.exceed"),
  /** 消息重复（已被去重） */
  /** Message duplicated */
  MESSAGE_DUPLICATED("B91509", "message.duplicated"),
  /** 跨渠道抑制 */
  /** Channel suppressed */
  CHANNEL_SUPPRESSED("B91510", "message.channel.suppressed");

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** 默认 HTTP 状态码：参数错误 */
  private static final int DEFAULT_HTTP_STATUS = 400;

  /** 默认重试等待时间（秒）：不重试 */
  private static final int DEFAULT_RETRY_AFTER_SECONDS = 0;

  /** HTTP 429：请求过于频繁（限流） */
  private static final int HTTP_TOO_MANY_REQUESTS = 429;

  /** HTTP 状态码 */
  private final int httpStatus;

  /** 是否可恢复（客户端是否应重试） */
  private final boolean retryable;

  /** 建议重试等待秒数 */
  private final int retryAfterSeconds;

  MessageExceptionCode(String code, String key) {
    this(code, key, DEFAULT_HTTP_STATUS, false, DEFAULT_RETRY_AFTER_SECONDS);
  }

  MessageExceptionCode(String code, String key, int httpStatus) {
    this(code, key, httpStatus, httpStatus == HTTP_TOO_MANY_REQUESTS, DEFAULT_RETRY_AFTER_SECONDS);
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
