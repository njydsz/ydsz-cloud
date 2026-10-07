package com.njydsz.common.jdbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * HikariCP 连接池指标采集配置。
 *
 * <p>控制 {@link com.njydsz.common.jdbc.metrics.HikariMetricsReporter} 的运行参数：
 *
 * <ul>
 *   <li>是否启用周期性指标采集
 *   <li>采集间隔（默认 60s）
 * </ul>
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     metrics:
 *       enabled: true
 *       interval-seconds: 60
 * }</pre>
 *
 * @author ydsz
 * @since 26.10.06
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.jdbc.metrics")
public class HikariMetricsProperties {

  /** 是否启用 HikariCP 指标采集与告警（默认 true） */
  private boolean isEnabled = true;

  /** 采集间隔秒数（默认 60） */
  private int intervalSeconds = 60;

  public boolean isEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(boolean isEnabled) {
    this.isEnabled = isEnabled;
  }

  public int getIntervalSeconds() {
    return intervalSeconds;
  }

  public void setIntervalSeconds(int intervalSeconds) {
    this.intervalSeconds = intervalSeconds;
  }
}
