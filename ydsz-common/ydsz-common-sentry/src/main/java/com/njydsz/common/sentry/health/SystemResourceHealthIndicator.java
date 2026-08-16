package com.njydsz.common.sentry.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.sentry.metrics.SystemMetricsCollector;

/**
 * 系统资源健康检查
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class SystemResourceHealthIndicator implements HealthIndicator {

  private final SystemMetricsCollector systemMetricsCollector;

  @Override
  public Health health() {
    try {
      Runtime runtime = Runtime.getRuntime();
      long maxMemory = runtime.maxMemory();
      long totalMemory = runtime.totalMemory();
      long freeMemory = runtime.freeMemory();
      long usedMemory = totalMemory - freeMemory;
      double memoryUsage = maxMemory > 0 ? (double) usedMemory / maxMemory : 0;

      Health.Builder builder = memoryUsage > 0.9 ? Health.down() : Health.up();
      return builder
          .withDetail("memory.max", maxMemory)
          .withDetail("memory.used", usedMemory)
          .withDetail("memory.free", freeMemory)
          .withDetail("memory.usage", String.format("%.2f%%", memoryUsage * 100))
          .withDetail("processors", runtime.availableProcessors())
          .withDetail(
              "systemMetricsCollector", systemMetricsCollector != null ? "active" : "inactive")
          .build();
    } catch (Exception e) {
      log.warn("[Sentry] 系统资源健康检查失败: {}", e.getMessage());
      return Health.down(e).build();
    }
  }
}
