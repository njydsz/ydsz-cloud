package com.njydsz.common.thread.alarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线程池告警配置属性。
 *
 * <p>通过 {@code ydsz.thread.alarm.*} 前缀配置告警系统的各项阈值和行为。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   thread:
 *     alarm:
 *       enabled: true
 *       eval-interval-ms: 30000
 *       suppress-window-ms: 120000
 *       queue-usage-threshold: 0.8
 *       active-near-max-threshold: 0.9
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@ConfigurationProperties(prefix = "ydsz.thread.alarm")
public class ThreadPoolAlarmProperties {

  /** 是否启用告警系统（默认 false）。 */
  private boolean isEnabled = false;

  /** 评估间隔毫秒数（默认 30000，即 30 秒）。 */
  private long evalIntervalMs = 30_000L;

  /** 告警去重抑制窗口毫秒数（默认 120000，即 2 分钟）。 */
  private long suppressWindowMs = 120_000L;

  /** 队列使用率阈值（默认 0.8，即 80%）。 */
  private double queueUsageThreshold = 0.8;

  /** 活跃线程接近最大值的比率阈值（默认 0.9，即 90%）。 */
  private double activeNearMaxThreshold = 0.9;

  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }

  public long getEvalIntervalMs() {
    return evalIntervalMs;
  }

  public void setEvalIntervalMs(long evalIntervalMs) {
    this.evalIntervalMs = evalIntervalMs;
  }

  public long getSuppressWindowMs() {
    return suppressWindowMs;
  }

  public void setSuppressWindowMs(long suppressWindowMs) {
    this.suppressWindowMs = suppressWindowMs;
  }

  public double getQueueUsageThreshold() {
    return queueUsageThreshold;
  }

  public void setQueueUsageThreshold(double queueUsageThreshold) {
    this.queueUsageThreshold = queueUsageThreshold;
  }

  public double getActiveNearMaxThreshold() {
    return activeNearMaxThreshold;
  }

  public void setActiveNearMaxThreshold(double activeNearMaxThreshold) {
    this.activeNearMaxThreshold = activeNearMaxThreshold;
  }
}
