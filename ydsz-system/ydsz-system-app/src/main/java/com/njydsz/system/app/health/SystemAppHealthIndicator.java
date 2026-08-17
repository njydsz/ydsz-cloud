package com.njydsz.system.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 系统管理模块 App 端健康检查指示器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SystemAppHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "system");
    details.put("platform", "app");
    return Health.up().withDetails(details).build();
  }
}
