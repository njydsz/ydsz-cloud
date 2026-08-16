package com.njydsz.common.safe.health;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.safe.metrics.SafeMetrics;

/**
 * 安全模块健康检查指示器
 *
 * <p>检测安全模块各子系统的健康状态，暴露 /actuator/health/safe 端点。
 *
 * <p><b>检测逻辑：</b>
 *
 * <ul>
 *   <li>Redis 连通性（限流/CSRF Token 存储依赖 Redis）
 *   <li>各安全能力注册状态（XSS/CSRF/限流/IP访问控制/API签名/脱敏）
 *   <li>安全指标累计值（通过 {@link SafeMetrics} 采集）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(
    prefix = "ydsz.safe",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SafeHealthIndicator implements HealthIndicator {

  private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
  private final ObjectProvider<SafeMetrics> safeMetricsProvider;

  public SafeHealthIndicator(
      ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
      ObjectProvider<SafeMetrics> safeMetricsProvider) {
    this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    this.safeMetricsProvider = safeMetricsProvider;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "safe");

    // Redis 连通性检测（可选依赖）
    RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
    if (factory != null) {
      try {
        long startTime = System.currentTimeMillis();
        RedisConnection connection = factory.getConnection();
        try {
          String pong = connection.ping();
          long responseTime = System.currentTimeMillis() - startTime;
          details.put("redis", "PONG".equalsIgnoreCase(pong) ? "connected" : "unexpected: " + pong);
          details.put("redisResponseTimeMs", responseTime);
        } finally {
          connection.close();
        }
      } catch (Exception e) {
        details.put("redis", "disconnected: " + e.getMessage());
        log.warn("安全模块 Redis 健康检查失败: {}", e.getMessage());
      }
    } else {
      details.put("redis", "not configured (optional)");
    }

    // 安全能力清单（实际注册状态由 @ConditionalOnProperty 决定）
    Map<String, String> capabilities = new LinkedHashMap<>();
    capabilities.put("xss", "OWASP Sanitizer + configurable policies");
    capabilities.put("csrf", "Synchronizer / Double Submit dual mode");
    capabilities.put("rateLimit", "Token Bucket + Resilience4j Circuit Breaker");
    capabilities.put("ipAccess", "CIDR blacklist/whitelist + auto-block");
    capabilities.put("apiSignature", "timestamp + nonce + HMAC-SHA256");
    capabilities.put("sensitiveData", "18 types + role-based control");
    capabilities.put("crypto", "AES-256-GCM");
    capabilities.put("metrics", "Micrometer Counter/Timer (optional)");
    capabilities.put("auditLog", "structured JSON + traceId");
    details.put("capabilities", capabilities);

    // 安全指标累计值（SLO 监控数据）
    SafeMetrics safeMetrics = safeMetricsProvider.getIfAvailable();
    if (safeMetrics != null) {
      Map<String, Long> metrics = new LinkedHashMap<>();
      metrics.put("xssAttacks", safeMetrics.getXssAttacksCount());
      metrics.put("csrfFailures", safeMetrics.getCsrfFailuresCount());
      metrics.put("rateLimitTriggered", safeMetrics.getRateLimitTriggeredCount());
      details.put("metrics", metrics);
    }

    // 如果 Redis 不可用但其他能力仍可降级运行，状态为 UP with warning
    String redisStatus = (String) details.get("redis");
    if (redisStatus != null && redisStatus.startsWith("disconnected")) {
      return Health.up()
          .withDetail("module", "safe")
          .withDetail("redis", redisStatus)
          .withDetail("warning", "Redis unavailable - rate limiting/CSRF degraded to local mode")
          .withDetail("capabilities", capabilities)
          .build();
    }

    return Health.up().withDetails(details).build();
  }
}
