package com.njydsz.common.sentry.health;

import com.njydsz.common.sentry.logging.AsyncLogPublisher;
import com.njydsz.common.sentry.logging.DualLogPublisher;
import com.njydsz.common.sentry.spi.LogPublisher;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.TraceContext;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

/**
 * Sentry 模块运行时元数据暴露。
 *
 * <p>通过 {@code /actuator/info} 端点暴露当前生效的 SPI 实现类名、采样率、队列大小等运行时元数据， 便于运维人员在不停机的情况下确认可观测性模块的实际工作状态。
 *
 * <p>暴露内容包括：
 *
 * <ul>
 *   <li>metrics.collector：指标采集器名称与可用性
 *   <li>logging.publisher：日志发布器名称与方案
 *   <li>tracing.tracer：链路追踪系统名称
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class SentryInfoContributor implements InfoContributor {

  private final MetricsCollector metricsCollector;
  private final LogPublisher logPublisher;
  private final TraceContext traceContext;

  @Override
  public void contribute(Info.Builder builder) {
    Map<String, Object> sentryInfo = new HashMap<>();

    if (metricsCollector != null) {
      sentryInfo.put("metrics.collector", metricsCollector.getName());
      sentryInfo.put("metrics.available", metricsCollector.isAvailable());
    }

    if (logPublisher != null) {
      sentryInfo.put("logging.publisher", logPublisher.getName());
      sentryInfo.put("logging.scheme", logPublisher.getScheme());
      sentryInfo.put("logging.available", logPublisher.isAvailable());

      // 异步日志运行时统计
      if (logPublisher instanceof AsyncLogPublisher async) {
        sentryInfo.put("logging.queueSize", async.getQueueSize());
        sentryInfo.put("logging.droppedTotal", async.getDroppedCount());
        sentryInfo.put("logging.publishedTotal", async.getTotalPublished());
      }

      // 双发子发布器摘要
      LogPublisher delegate =
          logPublisher instanceof AsyncLogPublisher async ? async.getDelegate() : logPublisher;
      if (delegate instanceof DualLogPublisher dual) {
        sentryInfo.put("logging.subPublishers", dual.getHealthSummary());
      }
    }

    if (traceContext != null) {
      sentryInfo.put("tracing.tracer", traceContext.getTracerName());
    }

    builder.withDetail("ydsz.sentry", sentryInfo);
  }
}
