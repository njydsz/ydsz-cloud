package com.njydsz.common.web.version;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * API 版本路由自动配置
 *
 * <p>通过 {@link WebMvcRegistrations} 机制替换默认的 {@link RequestMappingHandlerMapping}， 使用自定义的 {@link
 * ApiVersionRequestMappingHandlerMapping} 支持基于 URL 路径的接口版本路由。
 *
 * <p><b>默认禁用，需显式开启。</b>配置示例：
 *
 * <pre>
 * ydsz:
 *   api:
 *     version:
 *       enabled: true
 *       default-version: "1"
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ApiVersionRequestMappingHandlerMapping
 * @see ApiVersionProperties
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "ydsz.api.version",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@EnableConfigurationProperties(ApiVersionProperties.class)
public class ApiVersionAutoConfiguration implements WebMvcRegistrations {

  private final ApiVersionProperties properties;

  public ApiVersionAutoConfiguration(ApiVersionProperties properties) {
    this.properties = properties;
  }

  @Override
  public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
    return new ApiVersionRequestMappingHandlerMapping(properties);
  }
}
