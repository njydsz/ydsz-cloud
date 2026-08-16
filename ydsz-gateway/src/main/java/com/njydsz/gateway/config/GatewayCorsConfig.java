package com.njydsz.gateway.config;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 网关 CORS 跨域配置（P2-1 安全修复：单一可信来源）
 *
 * <p><b>背景：</b>此前配置使用多来源白名单（{@code allowedOrigins} 列表）， 且默认值包含 {@code *} 通配符。当 {@code
 * allowCredentials=true} 时， 浏览器规范禁止 {@code Access-Control-Allow-Origin: *}（RFC 6454 / Fetch
 * Standard）， 返回的响应会被浏览器拒绝，导致"凭据模式下不允许使用通配符"错误。
 *
 * <p><b>P2-1 修复内容：</b>
 *
 * <ul>
 *   <li>将多来源列表改为单一可信来源（{@code ydsz.gateway.cors.allowed-origin}）
 *   <li>启动时校验：凭据模式下禁止 {@code *} 来源（抛出 {@code IllegalStateException}）
 *   <li>使用 {@code allowedOriginPatterns} 严格匹配，确保 Origin 头精确匹配来源
 * </ul>
 *
 * <p><b>过滤器顺序：</b>CorsWebFilter 默认 Order 位于全局过滤器链最前端， 预检请求（OPTIONS）在进入鉴权过滤器前即被 CORS 处理并短路返回。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class GatewayCorsConfig {

  /** 凭据模式下禁止的通配符标记 */
  private static final String WILDCARD_ORIGIN = "*";

  /**
   * 注册响应式 CORS 过滤器
   *
   * <p>当 {@code ydsz.gateway.cors.enabled=false} 时跳过。 启动时校验凭据模式与通配符互斥，校验失败立即抛出异常阻止启动。
   *
   * @param corsProperties CORS 配置属性
   * @return CorsWebFilter Bean
   * @throws IllegalStateException 凭据模式下配置了 {@code *} 来源
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "ydsz.gateway.cors",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CorsWebFilter corsWebFilter(CorsProperties corsProperties) {
    validateCredentialsWithWildcard(corsProperties);

    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of(corsProperties.getAllowedOrigin()));
    config.setAllowedMethods(corsProperties.getAllowedMethods());
    config.setAllowedHeaders(corsProperties.getAllowedHeaders());
    config.setExposedHeaders(corsProperties.getExposedHeaders());
    config.setAllowCredentials(corsProperties.isAllowCredentials());
    config.setMaxAge(corsProperties.getMaxAgeSeconds());

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    log.info(
        "[Cors] 网关 CORS 已启用: origin={}, credentials={}, maxAge={}s",
        corsProperties.getAllowedOrigin(),
        corsProperties.isAllowCredentials(),
        corsProperties.getMaxAgeSeconds());
    return new CorsWebFilter(source);
  }

  /**
   * 校验凭据模式下是否配置了通配符来源
   *
   * <p>浏览器 Fetch Standard 规定：当 {@code Access-Control-Allow-Credentials: true} 时， {@code
   * Access-Control-Allow-Origin} 不得使用通配符 {@code *}。 Spring 的 CorsConfiguration 虽不会直接抛异常，但浏览器会拒绝该响应，
   * 故在启动时主动校验并抛出异常，避免运行时不一致行为。
   *
   * @param corsProperties CORS 配置属性
   * @throws IllegalStateException 凭据模式下配置了 {@code *} 来源
   */
  private void validateCredentialsWithWildcard(CorsProperties corsProperties) {
    if (!corsProperties.isAllowCredentials()) {
      return;
    }
    String origin = corsProperties.getAllowedOrigin();
    if (WILDCARD_ORIGIN.equals(origin)) {
      throw new IllegalStateException(
          "CORS 安全违规：allowCredentials=true 时禁止使用通配符 Origin (* ),"
              + "必须在 ydsz.gateway.cors.allowed-origin 中配置单一可信来源（如 https://ydsz.example.com）");
    }
    if (origin == null || origin.isBlank()) {
      throw new IllegalStateException(
          "CORS 安全违规：allowCredentials=true 时 allowed-origin 不能为空，"
              + "必须配置单一可信来源（如 https://ydsz.example.com）");
    }
  }
}
