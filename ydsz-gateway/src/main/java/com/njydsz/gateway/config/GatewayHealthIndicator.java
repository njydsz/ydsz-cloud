package com.njydsz.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.njydsz.common.safe.config.SecurityHeaderProperties;
import com.njydsz.gateway.filter.AuthGlobalFilter;

/**
 * 网关健康指标。
 *
 * <p>报告网关核心依赖和能力的运行状态。
 *
 * <h3>检查项</h3>
 *
 * <ul>
 *   <li>Redis 连通性（限流、黑名单、JWT 黑名单依赖）
 *   <li>安全头是否启用
 *   <li>限流是否启用
 *   <li>IP 黑白名单是否启用
 *   <li>网关指标
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class GatewayHealthIndicator implements HealthIndicator {

  private final ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider;
  private final ObjectProvider<SecurityHeaderProperties> securityHeaderProvider;
  private final ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider;
  private final ObjectProvider<IpAccessControlProperties> ipAccessControlProvider;
  private final ObjectProvider<AuthGlobalFilter> authFilterProvider;
  private final ObjectProvider<GatewayMetrics> gatewayMetricsProvider;

  /**
   * 构造网关健康指标。
   *
   * <p>使用 {@link ObjectProvider} 实现可选依赖，当某个 Bean 不存在时不影响健康检查。
   *
   * @param redisTemplateProvider Reactive Redis 客户端（可选）
   * @param securityHeaderProvider 安全响应头配置（可选）
   * @param rateLimitPropertiesProvider 限流配置（可选）
   * @param ipAccessControlProvider IP 访问控制配置（可选）
   * @param authFilterProvider 认证过滤器（可选）
   * @param gatewayMetricsProvider 网关指标（可选）
   */
  public GatewayHealthIndicator(
      ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
      ObjectProvider<SecurityHeaderProperties> securityHeaderProvider,
      ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider,
      ObjectProvider<IpAccessControlProperties> ipAccessControlProvider,
      ObjectProvider<AuthGlobalFilter> authFilterProvider,
      ObjectProvider<GatewayMetrics> gatewayMetricsProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.securityHeaderProvider = securityHeaderProvider;
    this.rateLimitPropertiesProvider = rateLimitPropertiesProvider;
    this.ipAccessControlProvider = ipAccessControlProvider;
    this.authFilterProvider = authFilterProvider;
    this.gatewayMetricsProvider = gatewayMetricsProvider;
  }

  /**
   * 汇总网关核心依赖与能力的运行状态。
   *
   * <p>逐个探测 Redis 连通性、安全响应头、限流、IP 访问控制、网关指标等，各项均通过 {@link ObjectProvider#getIfAvailable()}
   * 可选获取，缺失项标记 NOT_CONFIGURED 不影响整体。任一关键项（如鉴权过滤器）不可用时整体健康状态降级为 DOWN。
   *
   * @return 包含各项 detail 的健康快照
   */
  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    boolean healthy = true;

    // Redis 连通性检查
    ReactiveStringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      try {
        // 简单 Ping 检查
        redisTemplate.getConnectionFactory().getReactiveConnection().ping().block();
        details.put("redis.status", "UP");
      } catch (Exception e) {
        healthy = false;
        details.put("redis.status", "DOWN");
        details.put("redis.error", e.getMessage());
      }
    } else {
      details.put("redis.status", "NOT_CONFIGURED");
    }

    // 安全响应头状态
    SecurityHeaderProperties securityHeaders = securityHeaderProvider.getIfAvailable();
    if (securityHeaders != null) {
      details.put("securityHeaders.enabled", securityHeaders.isEnabled());
      details.put("securityHeaders.csp.enabled",
          securityHeaders.getCsp() != null && securityHeaders.getCsp().isEnabled());
      details.put("securityHeaders.hsts.enabled",
          securityHeaders.getHsts() != null && securityHeaders.getHsts().isEnabled());
    } else {
      details.put("securityHeaders.enabled", "NOT_CONFIGURED");
    }

    // 限流状态
    RateLimitProperties rateLimit = rateLimitPropertiesProvider.getIfAvailable();
    if (rateLimit != null) {
      details.put("rateLimit.enabled", rateLimit.isEnabled());
      details.put("rateLimit.perIp.enabled", rateLimit.getPerIp().isEnabled());
      details.put("rateLimit.perUser.enabled", rateLimit.getPerUser().isEnabled());
    } else {
      details.put("rateLimit.enabled", "NOT_CONFIGURED");
    }

    // IP 访问控制状态
    IpAccessControlProperties ipControl = ipAccessControlProvider.getIfAvailable();
    if (ipControl != null) {
      details.put("ipAccessControl.blacklistEnabled", ipControl.isBlacklistEnabled());
      details.put("ipAccessControl.whitelistEnabled", ipControl.isWhitelistEnabled());
      boolean hasWhitelist = ipControl.getWhitelist() != null && !ipControl.getWhitelist().isBlank();
      details.put("ipAccessControl.whitelistConfigured", hasWhitelist);
    } else {
      details.put("ipAccessControl.status", "NOT_CONFIGURED");
    }

    // 认证过滤器状态
    AuthGlobalFilter authFilter = authFilterProvider.getIfAvailable();
    if (authFilter != null) {
      details.put("authFilter.status", "CONFIGURED");
    } else {
      details.put("authFilter.status", "NOT_CONFIGURED");
      healthy = false;
    }

    // 网关指标状态
    GatewayMetrics metrics = gatewayMetricsProvider.getIfAvailable();
    details.put("gatewayMetrics.status", metrics != null ? "CONFIGURED" : "NOT_CONFIGURED");

    if (healthy) {
      return Health.up().withDetails(details).build();
    }
    return Health.down().withDetails(details).build();
  }
}
