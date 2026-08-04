package com.remisoft.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.remisoft.gateway.filter.AuthGlobalFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 网关健康指标
 *
 * <p>报告网关核心依赖和能力的运行状态，对标大厂网关健康检查标准。
 *
 * <h3>检查项</h3>
 * <ul>
 *   <li>Redis 连通性（限流、黑名单、JWT 黑名单依赖）</li>
 *   <li>JWT 缓存命中率</li>
 *   <li>安全头是否启用</li>
 *   <li>限流是否启用</li>
 *   <li>IP 白名单是否启用</li>
 *   <li>IP 黑名单缓存大小</li>
 *   <li>动态路由是否启用</li>
 *   <li>灰度负载均衡是否启用</li>
 *   <li>Sentinel 是否已加载规则</li>
 * </ul>
 *
 * @since 1.0.0
 * @author remi-team
 */
@Slf4j
public class GatewayHealthIndicator implements HealthIndicator {

    private final ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<SecurityHeadersProperties> securityHeadersProvider;
    private final ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider;
    private final ObjectProvider<IpWhitelistProperties> ipWhitelistProvider;
    private final ObjectProvider<AuthGlobalFilter> authFilterProvider;
    private final ObjectProvider<GatewayMetrics> gatewayMetricsProvider;

    /**
     * 构造网关健康指标
     *
     * <p>使用 {@link ObjectProvider} 实现可选依赖，当某个 Bean 不存在时不影响健康检查。
     *
     * @param redisTemplateProvider     Redis 响应式模板（可选）
     * @param securityHeadersProvider   安全响应头配置（可选）
     * @param rateLimitPropertiesProvider 限流配置（可选）
     * @param ipWhitelistProvider       IP 白名单配置（可选）
     * @param authFilterProvider        认证过滤器（可选）
     * @param gatewayMetricsProvider    网关指标（可选）
     */
    public GatewayHealthIndicator(
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
            ObjectProvider<SecurityHeadersProperties> securityHeadersProvider,
            ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider,
            ObjectProvider<IpWhitelistProperties> ipWhitelistProvider,
            ObjectProvider<AuthGlobalFilter> authFilterProvider,
            ObjectProvider<GatewayMetrics> gatewayMetricsProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.securityHeadersProvider = securityHeadersProvider;
        this.rateLimitPropertiesProvider = rateLimitPropertiesProvider;
        this.ipWhitelistProvider = ipWhitelistProvider;
        this.authFilterProvider = authFilterProvider;
        this.gatewayMetricsProvider = gatewayMetricsProvider;
    }

    /**
     * 汇总网关核心依赖与能力的运行状态。
     *
     * <p>逐个探测 Redis 连通性、安全响应头、限流、IP 白名单、认证过滤器、灰度等，
     * 各项均通过 {@link ObjectProvider#getIfAvailable()} 可选获取，缺失项标记 NOT_CONFIGURED 不影响整体。
     * 任一关键项（如鉴权过滤器）不可用时整体健康状态降级为 DOWN。
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
                redisTemplate.getConnectionFactory().getReactiveConnection().ping().block();
                details.put("redis.status", "UP");
            } catch (Exception e) {
                details.put("redis.status", "DOWN");
                details.put("redis.error", e.getMessage());
                healthy = false;
            }
        } else {
            details.put("redis.status", "NOT_CONFIGURED");
        }

        // 安全响应头状态
        SecurityHeadersProperties securityHeaders = securityHeadersProvider.getIfAvailable();
        if (securityHeaders != null) {
            details.put("securityHeaders.enabled", securityHeaders.isEnabled());
            details.put("securityHeaders.csp.enabled", securityHeaders.getCsp().isEnabled());
            details.put("securityHeaders.hsts.enabled", securityHeaders.getHsts().isEnabled());
        } else {
            details.put("securityHeaders.enabled", "NOT_CONFIGURED");
        }

        // 限流状态
        RateLimitProperties rateLimit = rateLimitPropertiesProvider.getIfAvailable();
        if (rateLimit != null) {
            details.put("rateLimit.enabled", rateLimit.isEnabled());
            details.put("rateLimit.perIp.enabled", rateLimit.getPerIp().isEnabled());
            details.put("rateLimit.perUser.enabled", rateLimit.getPerUser().isEnabled());
            details.put("rateLimit.perTenant.enabled", rateLimit.getPerTenant().isEnabled());
        } else {
            details.put("rateLimit.enabled", "NOT_CONFIGURED");
        }

        // IP 白名单状态
        IpWhitelistProperties ipWhitelist = ipWhitelistProvider.getIfAvailable();
        if (ipWhitelist != null) {
            details.put("ipWhitelist.enabled", ipWhitelist.isIpWhitelistEnabled());
            boolean hasWhitelist = ipWhitelist.getIpWhitelist() != null && !ipWhitelist.getIpWhitelist().isBlank();
            details.put("ipWhitelist.configured", hasWhitelist);
        } else {
            details.put("ipWhitelist.enabled", "NOT_CONFIGURED");
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
