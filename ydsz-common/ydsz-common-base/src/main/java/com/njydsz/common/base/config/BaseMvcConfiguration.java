package com.njydsz.common.base.config;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 基础配置（Web/App 共享）
 *
 * <p>子类提供具体的 {@link BaseCorsProperties} 和 {@link BaseTraceProperties} 实现， 以及注册自己的拦截器和过滤器 Bean。
 *
 * <p>JSON 序列化统一使用 YdszJson 引擎（通过 ydsz-common-json 的 JsonHttpMessageConverter 自动注册）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public abstract class BaseMvcConfiguration implements WebMvcConfigurer {

  /** CORS 跨域配置属性 */
  private final BaseCorsProperties corsProperties;

  /**
   * 构造 MVC 基础配置
   *
   * @param corsProperties CORS 配置属性
   */
  protected BaseMvcConfiguration(BaseCorsProperties corsProperties) {
    this.corsProperties = corsProperties;
  }

  /**
   * 获取 CORS 配置属性
   *
   * @return CORS 配置属性实例
   */
  protected BaseCorsProperties getCorsProperties() {
    return corsProperties;
  }

  /**
   * 注册 CORS 过滤器
   *
   * <p>通过 {@link BaseCorsProperties#isEnabled()} 控制是否生效， 配置由子类通过 {@code @ConfigurationProperties}
   * 绑定具体前缀。 禁用时返回禁用状态的 FilterRegistrationBean，避免 @Bean 返回 null。
   *
   * @return CORS 过滤器注册器（禁用时 setEnabled(false)）
   */
  @Bean
  public FilterRegistrationBean<CorsFilter> corsFilter() {
    FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>();
    registration.setName("corsFilter");

    if (!corsProperties.isEnabled()) {
      registration.setEnabled(false);
      return registration;
    }

    // CORS 安全加固 — 启动时校验配置安全性，输出警告日志
    List<String> securityWarnings = corsProperties.validateSecurity();
    for (String warning : securityWarnings) {
      log.warn("[CORS Security] {}", warning);
    }

    CorsConfiguration corsConfig = new CorsConfiguration();
    corsConfig.setAllowCredentials(corsProperties.isAllowCredentials());
    corsProperties.getAllowedOriginPatterns().forEach(corsConfig::addAllowedOriginPattern);
    corsProperties.getAllowedHeaders().forEach(corsConfig::addAllowedHeader);
    corsProperties.getAllowedMethods().forEach(corsConfig::addAllowedMethod);
    // 暴露响应头配置
    corsProperties.getExposedHeaders().forEach(corsConfig::addExposedHeader);
    corsConfig.setMaxAge(corsProperties.getMaxAge());

    UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();
    String pathPattern = corsProperties.getPathPattern();
    configSource.registerCorsConfiguration(pathPattern != null ? pathPattern : "/**", corsConfig);

    registration.setFilter(new CorsFilter(configSource));
    registration.setOrder(corsProperties.getOrder());
    return registration;
  }
}
