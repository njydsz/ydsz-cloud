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
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(COLLECTION_CAPACITY);
    details.put("module", "literule");
    details.put("platform", "app");
    return Health.up().withDetails(details).build();
  }
}
