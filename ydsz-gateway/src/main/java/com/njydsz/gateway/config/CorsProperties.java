package com.njydsz.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.core.constant.HeaderConstants;

/**
 * 网关 CORS 跨域配置属性
 *
 * <p>对标 Spring 官方推荐（CorsWebFilter）+ OWASP CORS 安全规范： 使用单一可信 Origin 模式，杜绝使用 {@code *} +
 * 凭据的组合（浏览器规范禁止， 且存在 CSRF / 数据泄露风险）。
 *
 * <p>配置示例（Nacos 共享配置下发）：
 *
 * <pre>{@code
 * ydsz:
 *   gateway:
 *     cors:
 *       allowed-origin: https://ydsz.example.com
 *       allowed-methods: GET,POST,PUT,DELETE,OPTIONS
 *       allowed-headers: "*"
 *       exposed-headers: X-Trace-Id,X-RateLimit-Limit,X-RateLimit-Remaining,X-RateLimit-Reset
 *       allow-credentials: true
 *       max-age-seconds: 3600
 * }</pre>
 *
 * <p><b>凭据模式与通配符互斥校验：</b> 当 {@code allow-credentials=true} 时，{@code allowed-origin} 不得为 {@code *}，
 * 必须在 {@link GatewayCorsConfig#corsWebFilter(CorsProperties)} 中校验。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "ydsz.gateway.cors")
public class CorsProperties {

  /**
   * 允许的单一可信来源（精确 Origin，不含路径）。
   *
   * <p>示例：{@code https://ydsz.example.com}。 默认值 {@code https://ydsz.example.com} 仅作为占位，生产环境必须通过
   * Nacos 配置替换。
   */
  private String allowedOrigin = "https://ydsz.example.com";

  /** 允许的 HTTP 方法 */
  private List<String> allowedMethods =
      new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

  /** 允许的请求头 */
  private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

  /** 暴露给浏览器 JS 的响应头 */
  private List<String> exposedHeaders =
      new ArrayList<>(
          List.of(
              HeaderConstants.TRACE_ID_HEADER,
              "X-Request-Id",
              "X-RateLimit-Limit",
              "X-RateLimit-Remaining",
              "X-RateLimit-Reset",
              "Retry-After",
              "X-API-Version"));

  /** 是否允许携带凭据（Cookie / Authorization） */
  private boolean allowCredentials = true;

  /** 预检请求缓存时间（秒） */
  private long maxAgeSeconds = 3600;

  /** 是否启用 CORS 过滤器（默认启用） */
  private boolean enabled = true;

  public String getAllowedOrigin() {
    return allowedOrigin;
  }

  public void setAllowedOrigin(String allowedOrigin) {
    this.allowedOrigin = allowedOrigin;
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
