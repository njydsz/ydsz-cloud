package com.njydsz.gateway;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

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
}
