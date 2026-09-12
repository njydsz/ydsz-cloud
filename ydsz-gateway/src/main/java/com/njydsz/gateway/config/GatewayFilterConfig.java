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

import com.njydsz.gateway.exception.GatewayExceptionHandler;

/**
 * 网关 Web 层过滤器配置（CORS + 全局异常处理 + 安全响应头）。
 *
 * <p>聚合 HTTP 入口层横切关注点的 Bean 定义：
 *
 * <ul>
 *   <li>{@link CorsWebFilter}：跨域预检与响应头注入
 *   <li>{@link GatewayExceptionHandler}：统一异常 → RFC 7807 / YdszResponse JSON 映射
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(
    {CorsProperties.class, SqlInjectionProperties.class, DeprecationProperties.class})
public class GatewayFilterConfig {

  /** 凭据模式下禁止使用的通配符来源标记（{@code *} 与 {@code allowCredentials=true} 互斥）。 */
  private static final String WILDCARD_ORIGIN = "*";

  // =========================================================================
  // CORS
  // =========================================================================

  /**
   * 注册响应式 CORS 过滤器。
   *
   * <p>当 {@code ydsz.gateway.cors.enabled=false} 时跳过。启动时校验凭据模式与通配符互斥，
   * 校验失败立即抛出异常阻止启动。
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
   * 校验凭据模式下是否配置了通配符来源。
   *
   * <p>浏览器 Fetch Standard 规定：当 {@code Access-Control-Allow-Credentials: true} 时，
   * {@code Access-Control-Allow-Origin} 不得使用通配符 {@code *}。
   * Spring 的 CorsConfiguration 虽不会直接抛异常，但浏览器会拒绝该响应，
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

  // =========================================================================
  // 全局异常处理
  // =========================================================================

  /**
   * 注册自定义网关异常处理器。
   *
   * <p>通过 {@code @Order(-2)} 确保优先于 Spring Boot 默认的 ErrorWebExceptionHandler。
   *
   * @return 网关异常处理器
   */
  @Bean
  public GatewayExceptionHandler gatewayErrorHandler() {
    return new GatewayExceptionHandler();
  }
}
