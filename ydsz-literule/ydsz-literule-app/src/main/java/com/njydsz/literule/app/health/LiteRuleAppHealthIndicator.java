package com.njydsz.literule.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 规则引擎模块 App 端健康检查指示器。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class LiteRuleAppHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "literule");
    details.put("platform", "app");
    return Health.up().withDetails(details).build();
  }
}
