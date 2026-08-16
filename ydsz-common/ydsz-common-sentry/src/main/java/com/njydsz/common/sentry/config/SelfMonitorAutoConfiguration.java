package com.njydsz.common.sentry.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.sentry.alerting.AlertConverger;
import com.njydsz.common.sentry.logging.AsyncLogPublisher;
import com.njydsz.common.sentry.spi.AlertPublisher;
import com.njydsz.common.sentry.spi.LogPublisher;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.SlaCollector;
import com.njydsz.common.sentry.spi.TraceContext;

/**
 * 自监控自动配置。
 *
 * <p>周期性上报 Sentry 各组件的可用性指标到 MetricsCollector， 解决"监控系统本身挂了没人知道"的问题。
 *
 * <p>Periodic reporting includes:
 *
 * <ul>
 *   <li>指标采集器 / 日志发布器 / 告警发布器可用性
 *   <li>异步日志队列积压数、丢弃总数、已发布总数
 *   <li>告警收敛的抑制率与总量
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
  MetricsAutoConfiguration.class,
  LoggingAutoConfiguration.class,
  AlertingAutoConfiguration.class
})
@EnableConfigurationProperties(SentryProperties.class)
public class SelfMonitorAutoConfiguration {

  /**
   * 装配 Sentry 自监控器。
   *
   * @param metricsCollector 指标采集器
   * @param logPublisher 日志发布器
   * @param alertPublisher 告警发布器
   * @param traceContext 链路上下文
   * @param slaCollector SLA 采集器
   * @return 自监控器
   */
  @Bean
  @ConditionalOnMissingBean(SentrySelfMonitor.class)
  public SentrySelfMonitor sentrySelfMonitor(
      MetricsCollector metricsCollector,
      LogPublisher logPublisher,
      AlertPublisher alertPublisher,
      TraceContext traceContext,
      SlaCollector slaCollector) {
    return new SentrySelfMonitor(
        metricsCollector, logPublisher, alertPublisher, traceContext, slaCollector);
  }

  /**
   * 自监控指标上报器。
   *
   * <p>定时上报 Sentry 各组件的自监控 Gauge 指标到 MetricsCollector， 供 Prometheus 告警规则使用。
   */
  @Slf4j
  public static class SentrySelfMonitor {

    private static final Map<String, String> EMPTY_TAGS = Map.of();

    private final MetricsCollector metricsCollector;
    private final LogPublisher logPublisher;
    private final AlertPublisher alertPublisher;
    private final TraceContext traceContext;
    private final SlaCollector slaCollector;

    public SentrySelfMonitor(
        MetricsCollector metricsCollector,
        LogPublisher logPublisher,
        AlertPublisher alertPublisher,
        TraceContext traceContext,
        SlaCollector slaCollector) {
      this.metricsCollector = metricsCollector;
      this.logPublisher = logPublisher;
      this.alertPublisher = alertPublisher;
      this.traceContext = traceContext;
      this.slaCollector = slaCollector;
      log.info("[Sentry] SentrySelfMonitor 初始化完成");
    }

    /**
     * 每 15 秒上报一次 Sentry 各组件的自监控 Gauge 指标。
     *
     * <p>整个方法用 try-catch 兜底并只打 debug 日志：自监控失败绝不能影响业务， 也不能因为反复打印 error 日志形成新的噪音源。
     */
    @Scheduled(fixedRate = 15000)
    public void reportSelfMetrics() {
      try {
        reportMetricsAvailability();
        reportLoggingMetrics();
        reportAlertingMetrics();
      } catch (Exception e) {
        log.debug("[Sentry] 自监控指标上报异常: {}", e.getMessage());
      }
    }

    private void reportMetricsAvailability() {
      if (metricsCollector != null) {
        metricsCollector.setGauge(
            "ydsz.sentry.metrics.available",
            "指标采集器可用性",
            EMPTY_TAGS,
            metricsCollector.isAvailable() ? 1.0 : 0.0);
      }
    }

    private void reportLoggingMetrics() {
      if (logPublisher == null) {
        return;
      }
      metricsCollector.setGauge(
          "ydsz.sentry.logging.available",
          "日志发布器可用性",
          EMPTY_TAGS,
          logPublisher.isAvailable() ? 1.0 : 0.0);
      if (logPublisher instanceof AsyncLogPublisher async) {
        metricsCollector.setGauge(
            "ydsz.sentry.logging.queue_size", "异步日志队列积压数", EMPTY_TAGS, async.getQueueSize());
        metricsCollector.setGauge(
            "ydsz.sentry.logging.dropped_total", "异步日志丢弃总数", EMPTY_TAGS, async.getDroppedCount());
        metricsCollector.setGauge(
            "ydsz.sentry.logging.published_total",
            "异步日志已发布总数",
            EMPTY_TAGS,
            async.getTotalPublished());
      }
    }

    private void reportAlertingMetrics() {
      if (alertPublisher == null) {
        return;
      }
      metricsCollector.setGauge(
          "ydsz.sentry.alerting.available",
          "告警发布器可用性",
          EMPTY_TAGS,
          alertPublisher.isAvailable() ? 1.0 : 0.0);
      if (alertPublisher instanceof AlertConverger converger) {
        metricsCollector.setGauge(
            "ydsz.sentry.alert.suppression_rate",
            "告警抑制率",
            EMPTY_TAGS,
            converger.getSuppressionRate());
        metricsCollector.setGauge(
            "ydsz.sentry.alert.total", "告警总数", EMPTY_TAGS, converger.getTotalAlerts());
        metricsCollector.setGauge(
            "ydsz.sentry.alert.suppressed", "被抑制告警数", EMPTY_TAGS, converger.getSuppressedAlerts());
      }
    }
  }
}
