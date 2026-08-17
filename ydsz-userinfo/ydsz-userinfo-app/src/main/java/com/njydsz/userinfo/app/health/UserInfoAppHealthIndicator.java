package com.njydsz.userinfo.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 用户信息模块 App 端健康检查指示器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class UserInfoAppHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "userinfo");
    details.put("platform", "app");
    return Health.up().withDetails(details).build();
  }
}
