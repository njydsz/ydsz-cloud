package com.njydsz.nextwiki.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NextWiki 移动端模块配置属性。
 *
 * <p>前缀 {@code nextwiki.app}，用于配置移动端特有的行为参数
 * （如推送、离线缓存、分页大小等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "nextwiki.app")
public class NextwikiAppProperties {

  /** 是否启用移动端 API */
  private boolean enabled = true;

  /** 移动端默认分页大小 */
  private int defaultPageSize = 20;

  /** 移动端最大分页大小 */
  private int maxPageSize = 100;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getDefaultPageSize() {
    return defaultPageSize;
  }

  public void setDefaultPageSize(int defaultPageSize) {
    this.defaultPageSize = defaultPageSize;
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  public void setMaxPageSize(int maxPageSize) {
    this.maxPageSize = maxPageSize;
  }
}
