package com.njydsz.gateway.config;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.njydsz.common.locales.util.I18n;
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
 * @author ydsz
 * @since 26.09.24
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(
    {CorsProperties.class, SqlInjectionProperties.class, DeprecationProperties.class,
     ApiKeyProperties.class})
public class GatewayFilterConfig {

  /** 凭据模式下禁止使用的通配符来源标记（{@code *} 与 {@code allowCredentials=true} 互斥）。 */
  private static final String WILDCARD_ORIGIN = "*";

  // =========================================================================
  // CORS
  // =========================================================================

  /**
   * 注册响应式 CORS 过滤器。
   *
   * <p>当 {@code ydsz.gateway.cors.enabled=false} 时跳过。启动时校验来源非空与凭据模式通配符互斥，
   * 校验失败立即抛出异常阻止启动。
   *
   * @param corsProperties CORS 配置属性
   * @return CorsWebFilter Bean
   * @throws IllegalStateException 未配置来源，或凭据模式下配置了 {@code *} 来源
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "ydsz.gateway.cors",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CorsWebFilter corsWebFilter(CorsProperties corsProperties) {
    List<String> originPatterns = parseOriginPatterns(corsProperties.getAllowedOrigin());
    validateOrigins(corsProperties, originPatterns);

    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(originPatterns);
    config.setAllowedMethods(corsProperties.getAllowedMethods());
    config.setAllowedHeaders(corsProperties.getAllowedHeaders());
    config.setExposedHeaders(corsProperties.getExposedHeaders());
    config.setAllowCredentials(corsProperties.isAllowCredentials());
    config.setMaxAge(corsProperties.getMaxAgeSeconds());

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    log.info(
        "[Cors] 网关 CORS 已启用: origins={}, credentials={}, maxAge={}s",
        originPatterns,
        corsProperties.isAllowCredentials(),
        corsProperties.getMaxAgeSeconds());
    return new CorsWebFilter(source);
  }

  /**
   * 把 {@code ydsz.gateway.cors.allowed-origin} 解析为 Origin 模式列表。
   *
   * <p>该配置项是字符串，约定支持逗号分隔多值（环境变量注入时的常见写法，如
   * {@code http://localhost:5600,https://ydsz.example.com}）。整串直交给 Spring 时，
   * 多值配置不会报错却永远匹配不上任何 Origin，表现为"CORS 配了但全线失败"。
   *
   * @param rawAllowedOrigin 原始配置值
   * @return 去空白、去空项后的模式列表（可能为空，由 {@link #validateOrigins} 判失败）
   */
  private List<String> parseOriginPatterns(String rawAllowedOrigin) {
    if (rawAllowedOrigin == null || rawAllowedOrigin.isBlank()) {
      return List.of();
    }
    return Arrays.stream(rawAllowedOrigin.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .toList();
  }

  /**
   * 校验 CORS 来源配置是否可用。
   *
   * <p>两条红线：
   *
   * <ol>
   *   <li>来源不得为空：空列表会让 CorsWebFilter 静默拒绝所有跨域请求，与"忘记配置 CORS"
   *       难以区分，故按启动失败处理而非静默放行。
   *   <li>凭据模式下不得使用裸通配符 {@code *}：Fetch Standard 规定
   *       {@code Access-Control-Allow-Credentials: true} 时响应头不能是 {@code *}，
   *       Spring 不报错但浏览器会拒绝响应。
   *       {@code http://localhost:*} 这类"带 scheme 的端口通配"不在禁止范围。
   * </ol>
   *
   * @param corsProperties CORS 配置属性
   * @param originPatterns 已解析的来源列表
   * @throws IllegalStateException 来源为空，或凭据模式下含 {@code *}
   */
  private void validateOrigins(CorsProperties corsProperties, List<String> originPatterns) {
    if (originPatterns.isEmpty()) {
      throw new IllegalStateException(I18n.message("gateway.cors.origin_required"));
    }
    if (corsProperties.isAllowCredentials() && originPatterns.contains(WILDCARD_ORIGIN)) {
      throw new IllegalStateException(
          I18n.message("gateway.cors.wildcard_origin_blocked"));
    }
  }

  // =========================================================================
  // 本地令牌桶（A1: RateLimitFilter L1 快路径）
  // =========================================================================

  /**
   * A1: 注册本地令牌桶 (L1 快路径组件)。
   *
   * <p>作为 RateLimitFilter 的 L1 快速判定：本地桶命中直接放行（纳秒级），耗尽时再走 Redis L2 分布式计数。
   * 减少 80% 正常流量路径上的 Redis 网络 IO。
   *
   * @param properties 限流配置
   * @return 本地令牌桶实例
   */
  @Bean
  public LocalRateLimiter localRateLimiter(GatewayRateLimitProperties properties) {
    return new LocalRateLimiter(properties);
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
