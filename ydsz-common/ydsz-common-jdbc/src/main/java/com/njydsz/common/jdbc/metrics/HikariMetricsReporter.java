package com.njydsz.common.jdbc.metrics;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.domain.AlertCategory;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.common.sentry.spi.AlertPublisher;

/**
 * HikariCP 连接池指标周期采集与告警。
 *
 * <p>每 60 秒通过 Micrometer {@link MeterRegistry} 采样 HikariCP 核心指标：
 *
 * <ul>
 *   <li>{@code hikaricp_connections_acquire_max} — 连接获取耗时最大值（纳秒转毫秒）
 *   <li>{@code hikaricp_connections_active} — 活跃连接数
 *   <li>{@code hikaricp_connections_max} — 连接池最大连接数
 *   <li>{@code hikaricp_connections_pending} — 等待获取连接的线程数
 * </ul>
 *
 * <p>当 P99 连接获取耗时 > 500ms、active 使用率 > 80% 或 pending > 5 时，
 * 通过 {@link AlertPublisher} 发布告警事件。
 *
 * <p><b>默认行为：</b>Micrometer MeterRegistry 不可用或 {@code ydzs.jdbc.metrics.enabled=false} 时静默跳过。
 *
 * @author ydsz
 * @since 26.10.06
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "ydsz.jdbc.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HikariMetricsReporter {

  /** 连接获取耗时告警阈值（毫秒） */
  private static final double ACQUIRE_P99_THRESHOLD_MILLIS = 500.0;

  /** 活跃连接使用率告警阈值 */
  private static final double ACTIVE_USAGE_THRESHOLD_PERCENT = 80.0;

  /** 等待获取连接的线程数告警阈值 */
  private static final int PENDING_THREADS_THRESHOLD = 5;

  /** 连接获取耗时指标名 */
  private static final String METRIC_ACQUIRE = "hikaricp_connections_acquire";

  /** 活跃连接数指标名 */
  private static final String METRIC_ACTIVE = "hikaricp_connections_active";

  /** 最大连接数指标名 */
  private static final String METRIC_MAX = "hikaricp_connections_max";

  /** 等待线程数指标名 */
  private static final String METRIC_PENDING = "hikaricp_connections_pending";

  private final MeterRegistry meterRegistry;

  private final AlertPublisher alertPublisher;

  /**
   * 每 60 秒执行一次指标采样（可通过 ydzs.jdbc.metrics.interval-seconds 配置周期）。
   */
  @Scheduled(fixedDelayString = "${ydsz.jdbc.metrics.interval-seconds:60}000")
  public void reportMetrics() {
    try {
      sampleAndAlert();
    } catch (Exception e) {
      log.warn("[HikariMetrics] 指标采集异常，跳过本次采样: reason={}", e.getMessage());
    }
  }

  /**
   * 采样 HikariCP 指标并触发告警判断。
   *
   * <p>使用 Micrometer MeterRegistry 直接查询指标值，避免反射。HikariCP 自动注册以下 meter：
   *
   * <ul>
   *   <li>hikaricp_connections_acquire（Timer，连接获取耗时）
   *   <li>hikaricp_connections_active（Gauge，活跃连接数）
   *   <li>hikaricp_connections_max（Gauge，最大连接数）
   *   <li>hikaricp_connections_pending（Gauge，等待连接线程数）
   * </ul>
   */
  private void sampleAndAlert() {
    // 检查是否存在 HikariCP 指标
    if (meterRegistry.find(METRIC_ACQUIRE).timer() == null) {
      log.debug("[HikariMetrics] 未找到 HikariCP 指标，跳过");
      return;
    }

    // 1. 连接获取耗时 P99 近似（均值 * 2 为粗略上界）
    double acquireP99Millis = getAcquireP99Millis();
    if (acquireP99Millis > ACQUIRE_P99_THRESHOLD_MILLIS) {
      publishAlert(
          "hikaricp_acquire_p99_high",
          AlertSeverity.P2,
          AlertCategory.PERFORMANCE,
          "HikariCP 连接获取耗时 P99 超标",
          String.format("连接获取耗时 P99=%.1fms (阈值=%.0fms)", acquireP99Millis, ACQUIRE_P99_THRESHOLD_MILLIS),
          acquireP99Millis);
    }

    // 2. 活跃连接使用率
    double activeUsagePercent = getActiveUsagePercent();
    if (activeUsagePercent > ACTIVE_USAGE_THRESHOLD_PERCENT) {
      publishAlert(
          "hikaricp_active_usage_high",
          AlertSeverity.P2,
          AlertCategory.CAPACITY,
          "HikariCP 活跃连接使用率超标",
          String.format("活跃连接使用率=%.1f%% (阈值=%.0f%%)", activeUsagePercent, ACTIVE_USAGE_THRESHOLD_PERCENT),
          activeUsagePercent);
    }

    // 3. 等待获取连接的线程数
    int pendingThreads = getPendingThreads();
    if (pendingThreads > PENDING_THREADS_THRESHOLD) {
      publishAlert(
          "hikaricp_pending_threads_high",
          AlertSeverity.P1,
          AlertCategory.AVAILABILITY,
          "HikariCP 等待连接线程数过多",
          String.format("等待获取连接的线程数=%d (阈值=%d)", pendingThreads, PENDING_THREADS_THRESHOLD),
          (double) pendingThreads);
    }

    log.debug(
        "[HikariMetrics] 指标采集完成: acquireP99={}ms activeUsage={}% pending={}",
        String.format("%.1f", acquireP99Millis),
        String.format("%.1f", activeUsagePercent),
        pendingThreads);
  }

  /**
   * 获取连接获取耗时 P99 毫秒数（均值 * 2 近似）。
   *
   * <p>Micrometer Timer 返回耗时单位为纳秒，此处转换为毫秒。
   *
   * @return P99 耗时（毫秒），无数据时返回 0.0
   */
  private double getAcquireP99Millis() {
    var timer = meterRegistry.find(METRIC_ACQUIRE).timer();
    if (timer == null) {
      return 0.0;
    }
    long count = timer.count();
    if (count == 0) {
      return 0.0;
    }
    // 使用均值 * 2 作为 P99 粗略估计
    double totalTimeMillis = timer.totalTime(TimeUnit.MILLISECONDS);
    double avgMillis = totalTimeMillis / count;
    return BigDecimal.valueOf(avgMillis)
        .multiply(BigDecimal.valueOf(2))
        .doubleValue();
  }

  /**
   * 获取活跃连接使用率（百分比）。
   *
   * @return 活跃连接使用率（0-100），无数据时返回 0.0
   */
  private double getActiveUsagePercent() {
    var activeGauge = meterRegistry.find(METRIC_ACTIVE).gauge();
    var maxGauge = meterRegistry.find(METRIC_MAX).gauge();

    double active = activeGauge != null ? activeGauge.value() : 0.0;
    double max = maxGauge != null ? maxGauge.value() : 0.0;

    if (max <= 0.0) {
      return 0.0;
    }
    return BigDecimal.valueOf(active)
        .divide(BigDecimal.valueOf(max), 4, BigDecimal.ROUND_HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .doubleValue();
  }

  /**
   * 获取等待获取连接的线程数。
   *
   * @return 等待线程数
   */
  private int getPendingThreads() {
    var pendingGauge = meterRegistry.find(METRIC_PENDING).gauge();
    if (pendingGauge == null) {
      return 0;
    }
    return (int) Math.round(pendingGauge.value());
  }

  /**
   * 发布告警事件。
   *
   * @param name 告警名称（唯一标识，用于去重）
   * @param severity 严重级别
   * @param category 告警分类
   * @param summary 告警摘要
   * @param description 告警详情
   * @param value 触发值
   */
  private void publishAlert(
      String name,
      AlertSeverity severity,
      AlertCategory category,
      String summary,
      String description,
      double value) {
    try {
      AlertEvent event = AlertEvent.builder()
          .name(name)
          .severity(severity)
          .category(category)
          .summary(summary)
          .description(description)
          .value(BigDecimal.valueOf(value))
          .labels(Map.of("source", "hikaricp", "reporter", "HikariMetricsReporter"))
          .build();

      boolean published = alertPublisher.publish(event);
      if (published) {
        log.info("[HikariMetrics] 告警发布成功: name={}, value={}", name, value);
      } else {
        log.debug("[HikariMetrics] 告警被收敛丢弃: name={}", name);
      }
    } catch (Exception e) {
      log.warn("[HikariMetrics] 告警发布异常: name={}, reason={}", name, e.getMessage());
    }
  }
}
