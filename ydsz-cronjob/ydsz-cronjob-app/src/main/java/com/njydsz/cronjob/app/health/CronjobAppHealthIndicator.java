package com.njydsz.cronjob.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 定时任务模块 App 端健康检查指示器。
 *
 * <p>Spring Boot 4.x 已将 Health/HealthIndicator 从 actuator 拆分至 spring-boot-health 模块
 * （包 {@code org.springframework.boot.health.contributor}），本实现按 4.x 新结构编写，
 * 依赖由 ydsz-cronjob-app/pom.xml 的 spring-boot-health 提供。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class CronjobAppHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(16);
    details.put("module", "cronjob");
    details.put("platform", "app");
    return Health.up().withDetails(details).build();
  }
}
