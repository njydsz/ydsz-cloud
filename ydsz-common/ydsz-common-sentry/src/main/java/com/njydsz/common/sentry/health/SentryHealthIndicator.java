package com.njydsz.common.sentry.health;

import com.njydsz.common.sentry.logging.AsyncLogPublisher;
import com.njydsz.common.sentry.logging.DualLogPublisher;
import com.njydsz.common.sentry.spi.LogPublisher;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Sentry 模块整体健康检查
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class SentryHealthIndicator implements HealthIndicator {

  private final MetricsCollector metricsCollector;
  private final LogPublisher logPublisher;
  private final TraceContext traceContext;

  @Override
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

    return builder.build();
  }
}
