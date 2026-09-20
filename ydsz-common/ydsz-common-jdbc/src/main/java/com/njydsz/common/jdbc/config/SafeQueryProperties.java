package com.njydsz.common.jdbc.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 安全查询配置属性（ORDER BY 注入防护 + 深度分页检测）
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     safe-query:
 *       enabled: true
 *       strict-mode: false
 *       order-by-whitelist: [id, created_at, updated_at]
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.jdbc.safe-query")
public class SafeQueryProperties {

  private boolean isEnabled = true;
  private boolean isStrictMode = false;
  private Set<String> orderByWhitelist;

  public boolean isEnabled() { return isEnabled; }
  public void setIsEnabled(boolean isEnabled) { this.isEnabled = isEnabled; }

  public boolean isStrictMode() { return isStrictMode; }
  public void setStrictMode(boolean isStrictMode) { this.isStrictMode = isStrictMode; }

  public Set<String> getOrderByWhitelist() { return orderByWhitelist; }
  public void setOrderByWhitelist(Set<String> orderByWhitelist) { this.orderByWhitelist = orderByWhitelist; }
}
