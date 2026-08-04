package com.njydsz.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 网关 CORS 跨域配置（P1-1 规范化）
 *
 * <p><b>背景：</b>此前 AuthGlobalFilter 仅简单放行 OPTIONS 预检请求，
 * 未声明标准 CORS 响应头（Access-Control-Allow-Origin 等），
 * 且未限制可信任来源，存在跨域安全风险与浏览器兼容性问题。
 *
 * <p><b>本配置：</b>通过 {@link CorsWebFilter} 声明式处理 CORS：
 * <ul>
 *   <li>白名单 Origin（支持通配符子域），默认 {@code *}（部署时须在 Nacos 配置收紧）</li>
 *   <li>暴露网关关键响应头（X-Trace-Id / X-RateLimit-*），供浏览器 JS 读取</li>
 *   <li>预检缓存 3600s，减少浏览器 OPTIONS 请求</li>
 *   <li>凭据模式与通配符 Origin 互斥校验（浏览器规范）</li>
 * </ul>
 *
 * <p><b>过滤器顺序：</b>CorsWebFilter 默认 Order 位于全局过滤器链最前端，
 * 预检请求（OPTIONS）在进入鉴权过滤器前即被 CORS 处理并短路返回，
 * AuthGlobalFilter 中的 OPTIONS 放行逻辑保留作为兜底（双重保障）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class GatewayCorsConfig {

    /**
     * 注册响应式 CORS 过滤器
     *
     * <p>当 {@code ydsz.gateway.cors.enabled=false} 时跳过（保留原 OPTIONS 放行逻辑）。
     *
     * @param corsProperties CORS 配置属性
     * @return CorsWebFilter Bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.gateway.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorsWebFilter corsWebFilter(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setExposedHeaders(corsProperties.getExposedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        log.info("[Cors] 网关 CORS 已启用: origins={}, credentials={}, maxAge={}s",
                corsProperties.getAllowedOrigins(), corsProperties.isAllowCredentials(),
                corsProperties.getMaxAgeSeconds());
        return new CorsWebFilter(source);
    }
}
