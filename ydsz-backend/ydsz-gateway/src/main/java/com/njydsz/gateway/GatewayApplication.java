package com.njydsz.gateway;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.service.ReactiveTokenBlacklistService;
import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.safe.crypto.NonceCache;
import com.njydsz.gateway.config.GatewayAlertService;
import com.njydsz.gateway.config.GatewayHealthIndicator;
import com.njydsz.gateway.config.GatewayMetrics;
import com.njydsz.gateway.config.IpWhitelistProperties;
import com.njydsz.gateway.config.RateLimitProperties;
import com.njydsz.gateway.config.SecurityHeadersProperties;
import com.njydsz.gateway.filter.AuthGlobalFilter;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * API 网关启动类
 *
 * <p>统一入口：路由分发、鉴权、限流、跨域、链路追踪
 *
 * <h3>网关职责</h3>
 * <ol>
 *   <li><b>路由转发</b>：基于 Nacos 动态路由 + Java 兜底路由（{@link RouteConfig}）</li>
 *   <li><b>统一鉴权</b>：JWT 校验 + 内部头签名（HMAC-SHA256 + nonce 防重放）</li>
 *   <li><b>多维限流</b>：IP/用户/租户三维令牌桶 + Sentinel API 分组限流</li>
 *   <li><b>安全防护</b>：IP 黑/白名单 + WebSocket 认证 + Payload 校验 + 安全响应头</li>
 *   <li><b>链路追踪</b>：W3C Trace Context（traceparent）+ X-Trace-Id 兼容</li>
 *   <li><b>灰度发布</b>：基于 Nacos metadata 的灰度路由 + 加权轮询</li>
 *   <li><b>监控告警</b>：Prometheus 指标 + 钉钉/飞书 IM 告警</li>
 * </ol>
 *
 * <h3>过滤器执行顺序</h3>
 * <pre>
 *   HIGHEST_PRECEDENCE       W3CTraceContextFilter      (生成 traceparent)
 *   HIGHEST_PRECEDENCE + 1   AccessLogGlobalFilter     (结构化访问日志)
 *   HIGHEST_PRECEDENCE + 3   PayloadValidationFilter   (请求体大小校验)
 *   HIGHEST_PRECEDENCE + 3   IpBlacklistFilter         (动态 IP 黑名单)
 *   HIGHEST_PRECEDENCE + 5   IpWhitelistFilter         (IP 白名单)
 *   HIGHEST_PRECEDENCE + 8   WebSocketAuthFilter       (WebSocket 独立鉴权)
 *   HIGHEST_PRECEDENCE + 10  AuthGlobalFilter          (主鉴权 + 内部头注入)
 *   HIGHEST_PRECEDENCE + 15  ApiKeyAuthFilter          (API Key 备选认证)
 *   HIGHEST_PRECEDENCE + 20  GrayLoadBalancerRequestFilter (灰度标识注入)
 *   HIGHEST_PRECEDENCE + 100 ReactiveLoadBalancerClientFilter (Spring Cloud LB)
 * </pre>
 *
 * @since 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({
        RateLimitProperties.class,
        SecurityHeadersProperties.class,
        IpWhitelistProperties.class
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * P3-1: 注册网关健康指标 Bean
     */
    @Bean
    public GatewayHealthIndicator gatewayHealthIndicator(
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
            ObjectProvider<SecurityHeadersProperties> securityHeadersProvider,
            ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider,
            ObjectProvider<IpWhitelistProperties> ipWhitelistProvider,
            ObjectProvider<AuthGlobalFilter> authFilterProvider,
            ObjectProvider<GatewayMetrics> gatewayMetricsProvider) {
        return new GatewayHealthIndicator(redisTemplateProvider, securityHeadersProvider,
                rateLimitPropertiesProvider, ipWhitelistProvider, authFilterProvider,
                gatewayMetricsProvider);
    }

    /**
     * GAP-P0-1: 注册 Reactive Token 黑名单服务
     *
     * <p>复用 ydsz-common-auth 的 TokenBlacklistBloomFilter + SHA-256 摘要 key，
     * 替代网关手写的 Redis 黑名单检查。
     */
    @Bean
    public ReactiveTokenBlacklistService reactiveTokenBlacklistService(
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
            AuthProperties authProperties) {
        return new ReactiveTokenBlacklistService(redisTemplateProvider, authProperties);
    }

    /**
     * GAP-P0-3: 注册 Nonce 防重放缓存
     *
     * <p>复用 ydsz-common-safe 的 NonceCache，网关生成 nonce 时存储，
     * 下游服务通过 X-Internal-Nonce 头接收后调用 verifyAndConsume() 双重校验。
     */
    @Bean
    public NonceCache nonceCache() {
        return new NonceCache();
    }

    /**
     * GAP-P1-1 + GAP-P1-2: 注册网关告警通知服务
     *
     * <p>集成 ydsz-common-notify 的 NotifyService，在限流触发、黑名单命中、
     * 下游 502/504 等关键事件时发送钉钉/飞书 IM 通知。
     */
    @Bean
    public GatewayAlertService gatewayAlertService(
            ObjectProvider<NotifyService> notifyServiceProvider) {
        return new GatewayAlertService(notifyServiceProvider);
    }
}
