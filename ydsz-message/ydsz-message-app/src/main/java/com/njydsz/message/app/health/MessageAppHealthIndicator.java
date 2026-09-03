package com.njydsz.message.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 消息中心模块 App 端健康检查指示器。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class MessageAppHealthIndicator implements HealthIndicator {

  /** 健康详情 Map 初始容量 */
  private static final int DETAILS_CAPACITY = 16;

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(DETAILS_CAPACITY);
    details.put("module", "message");
    details.put("platform", "app");
    return Health.up().withDetails(details).build();
  }
}
