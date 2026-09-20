package com.njydsz.common.jdbc.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 慢 SQL 监控配置属性
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     slow-sql:
 *       enabled: true
 *       threshold-millis: 1000
 *       alert-threshold-millis: 3000
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.jdbc.slow-sql")
public class SlowSqlProperties {

  private boolean isEnabled = false;

  @Min(1)
  private long thresholdMillis = 1000L;

  @Min(1)
  private long alertThresholdMillis = 3000L;

  public boolean isEnabled() { return isEnabled; }
  public void setIsEnabled(boolean isEnabled) { this.isEnabled = isEnabled; }

  public long getThresholdMillis() { return thresholdMillis; }
  public void setThresholdMillis(long thresholdMillis) { this.thresholdMillis = thresholdMillis; }

  public long getAlertThresholdMillis() { return alertThresholdMillis; }
  public void setAlertThresholdMillis(long alertThresholdMillis) { this.alertThresholdMillis = alertThresholdMillis; }
}
