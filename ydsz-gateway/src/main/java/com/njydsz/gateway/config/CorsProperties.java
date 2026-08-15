package com.njydsz.gateway.config;

import com.njydsz.common.core.constant.HeaderConstants;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关 CORS 跨域配置属性
 *
 * <p>对标 Spring 官方推荐（CorsWebFilter）+ 腾讯 TSF 网关规范：
 * 显式声明可信任 Origin 白名单，杜绝使用 {@code *} + 凭据的组合（浏览器规范禁止，
 * 且存在安全风险）。
 *
 * <p>配置示例（Nacos 共享配置下发）：
 * <pre>{@code
 * ydsz:
 *   gateway:
 *     cors:
 *       allowed-origins:
 *         - https://ydsz.example.com
 *         - https://*.example.com
 *       allowed-methods: GET,POST,PUT,DELETE,OPTIONS
 *       allowed-headers: "*"
 *       exposed-headers: X-Trace-Id,X-RateLimit-Limit,X-RateLimit-Remaining,X-RateLimit-Reset
 *       allow-credentials: true
 *       max-age-seconds: 3600
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ydsz.gateway.cors")
public class CorsProperties {

    /** 允许的源（支持 Ant 风格通配符，如 https://*.example.com） */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));

    /** 允许的 HTTP 方法 */
    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

    /** 允许的请求头 */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    /** 暴露给浏览器 JS 的响应头 */
    private List<String> exposedHeaders = new ArrayList<>(List.of(
            HeaderConstants.TRACE_ID_HEADER,
            "X-Request-Id",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "Retry-After",
            "X-API-Version"
    ));

    /** 是否允许携带凭据（Cookie / Authorization） */
    private boolean allowCredentials = true;

    /** 预检请求缓存时间（秒） */
    private long maxAgeSeconds = 3600;

    /** 是否启用 CORS 过滤器（默认启用） */
    private boolean enabled = true;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
