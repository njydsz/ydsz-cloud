package com.njydsz.common.jdbc.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
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
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.jdbc.slow-sql")
public class SlowSqlProperties {

  /** 是否启用慢 SQL 监控（默认 false） */
  private boolean enabled = false;

  /** 慢 SQL 检测阈值（毫秒），超过此阈值的 SQL 将被记录警告 */
  @Min(1)
  private long thresholdMillis = 1000L;

  /** 慢 SQL 告警阈值（毫秒），超过此值输出告警日志并打印调用堆栈 */
  @Min(1)
  private long alertThresholdMillis = 3000L;
}
