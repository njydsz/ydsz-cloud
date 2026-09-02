package com.njydsz.common.web.config;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import com.njydsz.common.web.filter.TenantMdcFilter;

/**
 * 从 CoreAutoConfiguration 迁出的 Web 层自动配置。
 *
 * <p>注册 Servlet Filter 和过滤器配置属性绑定，这些不属于 L1 基础设施层。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(
    prefix = "ydsz.core",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(FilterIgnoreProperties.class)
public class WebCoreAutoConfiguration {

  /** 租户 MDC 过滤器默认顺序（原 CoreProperties 默认值，ydsz-common-core 精简后改为常量）。 */
  private static final int DEFAULT_TENANT_MDC_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

  /**
   * 注册租户 MDC 过滤器，将 tenantId/userId/traceId 写入 SLF4J MDC。
   *
   * @return FilterRegistrationBean
   */
  @Bean
  @ConditionalOnClass(Filter.class)
  @ConditionalOnMissingBean(name = "tenantMdcFilter")
  @ConditionalOnProperty(
      prefix = "ydsz.core.tenant-mdc-filter",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FilterRegistrationBean<TenantMdcFilter> tenantMdcFilter() {
    FilterRegistrationBean<TenantMdcFilter> registration =
        new FilterRegistrationBean<>(new TenantMdcFilter());
    registration.setOrder(DEFAULT_TENANT_MDC_FILTER_ORDER);
    registration.setName("tenantMdcFilter");
    return registration;
  }
}
