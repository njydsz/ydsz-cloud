package com.njydsz.common.safe.alert;

import com.njydsz.common.safe.audit.SecurityAuditLogger;
import com.njydsz.common.safe.metrics.SafeMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * 安全事件监听器
 *
 * <p>串联安全事件处理链：当 {@link SecurityEventPublisher} 发布安全事件后， 此监听器负责将事件分发给所有下游处理器：
 *
 * <ul>
 *   <li>{@link SafeMetrics} — 采集 Micrometer 指标（Counter/Timer）
 *   <li>{@link SecurityAuditLogger} — 记录结构化 JSON 审计日志（含 traceId）
 * </ul>
 *
 * <p>安全事件处理链：
 *
 * <pre>{@code
 * SecurityEventPublisher.publish(event)
 *   → Spring ApplicationEvent
 *     → SecurityEventListener.onSecurityEvent(event)
 *       → SafeMetrics.recordSecurityEvent(event)     // Micrometer 指标
 *       → SecurityAuditLogger.log(event)             // 审计日志
 *       → SecurityEventAggregator.onSecurityEvent(event)  // 自动封禁检查
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SecurityEventListener {

  private static final Logger log = LoggerFactory.getLogger(SecurityEventListener.class);

  private final SafeMetrics safeMetrics;
  private final SecurityAuditLogger auditLogger;

  /**
   * @param safeMetrics Micrometer 指标采集器（可为 null，降级跳过指标采集）
   * @param auditLogger 安全审计日志记录器（可为 null，降级跳过审计日志）
   */
  public SecurityEventListener(SafeMetrics safeMetrics, SecurityAuditLogger auditLogger) {
    this.safeMetrics = safeMetrics;
    this.auditLogger = auditLogger;
    log.info(
        "安全事件监听器初始化: metrics={}, audit={}",
        safeMetrics != null ? "enabled" : "disabled",
        auditLogger != null ? "enabled" : "disabled");
  }

  /**
   * 监听安全事件并分发给下游处理器
   *
   * @param event 安全事件
   */
  @EventListener
  public void onSecurityEvent(SecurityEvent event) {
    if (event == null) {
      return;
    }

    if (safeMetrics != null) {
      try {
        safeMetrics.recordSecurityEvent(event);
      } catch (Exception e) {
        log.debug("安全事件指标采集失败: {}", e.getMessage());
      }
    }

    if (auditLogger != null) {
      try {
        auditLogger.log(event);
      } catch (Exception e) {
        log.debug("安全审计日志记录失败: {}", e.getMessage());
      }
    }
  }
}
