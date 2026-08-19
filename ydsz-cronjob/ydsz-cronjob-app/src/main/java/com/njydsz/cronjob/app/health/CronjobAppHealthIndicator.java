package com.njydsz.cronjob.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * 定时任务模块 App 端健康检查指示器。
 *
 * <p>P0-FIX: 原实现引用不存在的包 {@code org.springframework.boot.health.contributor.*}（Spring Boot
 * 无此包），修正为 Spring Boot Actuator 标准包 {@code org.springframework.boot.actuate.health.*}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CronjobAppHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "cronjob");
    details.put("platform", "app");
    return Health.up().withDetails(details).build();
  }
}
