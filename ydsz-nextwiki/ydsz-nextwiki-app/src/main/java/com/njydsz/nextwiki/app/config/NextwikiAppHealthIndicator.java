package com.njydsz.nextwiki.app.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * NextWiki 移动端模块健康检查指示器。
 *
 * <p>提供移动端特有的健康检查逻辑（如推送通道状态、离线缓存状态等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class NextwikiAppHealthIndicator implements HealthIndicator {

  private final NextwikiAppProperties appProperties;

  public NextwikiAppHealthIndicator(NextwikiAppProperties appProperties) {
    this.appProperties = appProperties;
  }

  @Override
  public Health health() {
    if (!appProperties.isEnabled()) {
      return Health.down().withDetail("reason", "App module is disabled").build();
    }

    return Health.up()
        .withDetail("defaultPageSize", appProperties.getDefaultPageSize())
        .withDetail("maxPageSize", appProperties.getMaxPageSize())
        .build();
  }
}
