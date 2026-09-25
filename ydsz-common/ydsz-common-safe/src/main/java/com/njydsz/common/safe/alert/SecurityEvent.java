package com.njydsz.common.safe.alert;

import java.io.Serializable;
import java.time.Instant;

/**
 * 安全事件值对象，封装一次攻击检测的完整上下文。
 *
 * <p>由安全过滤器（XSS/SQL注入/CSRF/IP访问控制/API签名）检测到攻击时构造，
 * 通过 {@link com.njydsz.common.safe.alert.SecurityEventPublisher} 发布，
 * 供 {@link com.njydsz.common.safe.metrics.SafeMetrics} 指标采集、
 * {@link com.njydsz.common.safe.audit.SecurityAuditLogger} 审计日志、
 * {@link com.njydsz.common.safe.alert.SecurityEventAggregator} 自动封禁等下游消费。
 *
 * <p>攻击载荷截断到 200 字符，防止恶意超长 payload 膨胀内存与日志存储。
 *
 * <p>本类为不可变对象，线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SecurityEvent implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final int MAX_PAYLOAD_LENGTH = 200;

  /** 事件类型 */
  private final SecurityEventType eventType;

  /** 请求 URI */
  private final String requestUri;

  /** 来源 IP */
  private final String sourceIp;

  /** 用户代理 */
  private final String userAgent;

  /** 攻击载荷（截断到 200 字符） */
  private final String attackPayload;

  /** 时间戳 */
  private final Instant timestamp;

  /** 严重级别 */
  private final Severity severity;

  public SecurityEvent(
      SecurityEventType eventType,
      String requestUri,
      String sourceIp,
      String userAgent,
      String attackPayload,
      Severity severity) {
    this.eventType = eventType;
    this.requestUri = requestUri;
    this.sourceIp = sourceIp;
    this.userAgent = userAgent;
    this.attackPayload = truncate(attackPayload, MAX_PAYLOAD_LENGTH);
    this.timestamp = Instant.now();
    this.severity = severity;
  }

  /** 严重级别 */
  public enum Severity {
    /** 低危 */
    LOW,
    /** 中危 */
    MEDIUM,
    /** 高危 */
    HIGH,
    /** 严重 */
    CRITICAL
  }

  public SecurityEventType getEventType() {
    return eventType;
  }

  public String getRequestUri() {
    return requestUri;
  }

  public String getSourceIp() {
    return sourceIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getAttackPayload() {
    return attackPayload;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public Severity getSeverity() {
    return severity;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...";
  }
}
