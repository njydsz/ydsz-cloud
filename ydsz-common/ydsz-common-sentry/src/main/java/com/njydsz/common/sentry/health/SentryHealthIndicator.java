package com.njydsz.common.sentry.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.sentry.alerting.AlertConverger;
import com.njydsz.common.sentry.logging.AsyncLogPublisher;
import com.njydsz.common.sentry.logging.DualLogPublisher;
import com.njydsz.common.sentry.spi.LogPublisher;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.SlaCollector;
import com.njydsz.common.sentry.spi.TraceContext;

/**
 * Sentry 模块整体健康检查
 *
 * <p>聚合指标 / 日志 / 链路 / 告警 / SLA 五条通道的可用性到 Actuator health 端点， 并公开告警收敛器与
 * SLA 采集器的内部统计指标。
 *
 * <p>26.09.20 变更：新增 {@code alert.converger.*} 和 {@code sla.*} 健康详情字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class SentryHealthIndicator implements HealthIndicator {

  private final MetricsCollector metricsCollector;
  private final LogPublisher logPublisher;
  private final TraceContext traceContext;

  /** 告警收敛器（可选），用于暴露内部抑制统计 */
  private final AlertConverger alertConverger;

  /** SLA 采集器（可选），用于暴露内部可用状态 */
  private final SlaCollector slaCollector;

  /**
   * 构造健康探针（不带告警和 SLA 的内部指标）。
   *
   * @param metricsCollector 指标采集器
   * @param logPublisher 日志发布器
   * @param traceContext 链路上下文
   */
  public SentryHealthIndicator(
      MetricsCollector metricsCollector, LogPublisher logPublisher, TraceContext traceContext) {
    this(metricsCollector, logPublisher, traceContext, null, null);
  }

  @Override
  /**
   * health。
   * @return 结果
   */
  public Health health() {
    Health.Builder builder = Health.up();

    if (metricsCollector != null) {
      builder
          .withDetail("metrics.collector", metricsCollector.getName())
          .withDetail("metrics.available", metricsCollector.isAvailable());
    }

    if (logPublisher != null) {
      builder
          .withDetail("logging.publisher", logPublisher.getName())
          .withDetail("logging.scheme", logPublisher.getScheme())
          .withDetail("logging.available", logPublisher.isAvailable());

      // 暴露 DualLogPublisher 子发布器健康状态
      if (logPublisher instanceof DualLogPublisher dual) {
        builder.withDetail("logging.subPublishers", dual.getHealthSummary());
      }

      // 暴露 AsyncLogPublisher 队列统计
      if (logPublisher instanceof AsyncLogPublisher async) {
        builder
            .withDetail("logging.queueSize", async.getQueueSize())
            .withDetail("logging.droppedCount", async.getDroppedCount())
            .withDetail("logging.totalPublished", async.getTotalPublished());
        LogPublisher delegate = async.getDelegate();
        if (delegate instanceof DualLogPublisher dualDelegate) {
          builder.withDetail("logging.subPublishers", dualDelegate.getHealthSummary());
        }
      }

      if (!logPublisher.isAvailable()) {
        builder.down();
      }
    }

    if (traceContext != null) {
      builder
          .withDetail("tracing.tracer", traceContext.getTracerName())
          .withDetail("tracing.tracing", traceContext.isTracing());
    }

    // 暴露告警收敛器内部指标
    if (alertConverger != null) {
      builder
          .withDetail("alert.converger.totalAlerts", alertConverger.getTotalAlerts())
          .withDetail("alert.converger.suppressedAlerts", alertConverger.getSuppressedAlerts())
          .withDetail("alert.converger.suppressionRate", alertConverger.getSuppressionRate())
          .withDetail("alert.converger.activeSilenceCount", alertConverger.getActiveSilenceCount())
          .withDetail("alert.available", alertConverger.isAvailable());
      if (!alertConverger.isAvailable()) {
        builder.down();
      }
    }

    // 暴露 SLA 采集器可用性
    if (slaCollector != null) {
      builder.withDetail("sla.available", slaCollector.isAvailable());
    }

    return builder.build();
  }
}
