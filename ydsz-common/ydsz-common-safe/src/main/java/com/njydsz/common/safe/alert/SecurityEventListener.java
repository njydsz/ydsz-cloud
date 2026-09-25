package com.njydsz.common.safe.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import com.njydsz.common.safe.metrics.SafeMetrics;

/**
 * 安全事件监听器
 *
 * <p>串联安全事件处理链：当 {@link SecurityEventPublisher} 发布安全事件后， 此监听器负责将事件分发给下游处理器：
 *
 * <ul>
 *   <li>{@link SafeMetrics} — 采集 Micrometer 指标（Counter/Timer）
 * </ul>
 *
 * <p>安全审计日志已迁移至 ydzs-common-audit 模块（AuditAspect + AuditRecorder），
 * 本监听器不再重复记录审计日志。安全事件处理链：
 *
 * <pre>{@code
 * SecurityEventPublisher.publish(event)
 *   → Spring ApplicationEvent
 *     → SecurityEventListener.onSecurityEvent(event)
 *       → SafeMetrics.recordSecurityEvent(event)     // Micrometer 指标
 *       → SecurityEventAggregator.onSecurityEvent(event)  // 自动封禁检查
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SecurityEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityEventListener.class);

  private final SafeMetrics safeMetrics;

  /**
   * 构造安全事件监听器，绑定指标采集下游链路。
   *
   * <p>依赖允许为 {@code null}：缺失时 {@link #onSecurityEvent(SecurityEvent)} 会静默跳过对应环节，
   * 保证安全事件的处理永不因观测组件缺失而中断。
   *
   * @param safeMetrics Micrometer 指标采集器（可为 null，降级跳过指标采集）
   */
  public SecurityEventListener(SafeMetrics safeMetrics) {
    this.safeMetrics = safeMetrics;
    LOG.info("安全事件监听器初始化: metrics={}", safeMetrics != null ? "enabled" : "disabled");
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
        LOG.debug("安全事件指标采集失败: {}", e.getMessage());
      }
    }
  }
}
