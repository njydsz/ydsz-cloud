package com.njydsz.common.web.config.sqlaudit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQL 数据权限自检配置属性
 *
 * <p>控制 {@link com.njydsz.common.web.health.SqlAuditHealthIndicator} 的启动自检行为。
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz-common:
 *   jdbc:
 *     sql-audit:
 *       enabled: true
 * }</pre>
 *
 * <p><b>fail-fast 策略：</b>当 {@code enabled=true} 但 {@code ydsz-common-jdbc} 的数据权限拦截器
 * （{@code RowPermissionInnerInterceptor} / {@code ColPermissionInnerInterceptor}）
 * 未出现在 MP 拦截器链中时，{@code HealthIndicator} 返回 {@code DOWN} 并打印明确错误日志。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ConfigurationProperties(prefix = "ydsz-common.jdbc.sql-audit")
public class SqlAuditProperties {

  /** 是否启用数据权限拦截器启动自检（默认 false，保持对无消费数据源模块的兼容性） */
  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
