package com.njydsz.common.base.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import com.njydsz.common.base.constant.FilterOrder;
import com.njydsz.common.base.filter.RequestBodySizeLimitFilter;
import com.njydsz.common.base.filter.RequestContextCleanupFilter;
import com.njydsz.common.base.filter.SecurityHeadersFilter;
import com.njydsz.common.base.filter.TraceFilter;
import com.njydsz.common.base.health.BaseHealthIndicator;
import com.njydsz.common.base.health.CoreHealthIndicator;

/**
 * Base 模块自动配置
 *
 * <p>提供 Web/App 公共基座层的自动装配能力，包括：
 *
 * <ul>
 *   <li>RequestContext 清理过滤器
 *   <li>链路追踪过滤器（TraceFilter）
 *   <li>安全响应头过滤器（SecurityHeadersFilter）
 *   <li>安全响应头配置属性绑定
 *   <li>健康指标（BaseHealthIndicator，需 actuator 依赖）
 * </ul>
 *
 * <p>注意：BaseCorsProperties 和 BaseTraceProperties 为抽象基类， 实际配置由 Web/App 子模块通过
 * {@code @ConfigurationProperties} 注解提供具体前缀。 若业务方直接使用 base 模块，请继承这些基类并指定自己的前缀。
 *
 * <p>文档相关健康指标由本模块内 {@code ydsz.common.base.config.DocAutoConfiguration} 提供。
 *
 * <p>横切点执行顺序参考 {@code docs/BASE_INTERCEPTOR_ORDER.md}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(
    prefix = "ydsz.base",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties({BaseSecurityHeadersProperties.class, BaseRequestProperties.class})
public class BaseAutoConfiguration {

  /**
   * 请求体大小限制过滤器
   *
   * <p>在请求到达 Controller 之前检查 Content-Length， 超过配置的阈值时直接返回 413 错误。
   *
   * @param properties 请求体配置属性
   * @return FilterRegistrationBean
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "ydsz.base.request",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(
      BaseRequestProperties properties) {
    FilterRegistrationBean<RequestBodySizeLimitFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new RequestBodySizeLimitFilter(properties.getMaxBodySize()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
    registration.addUrlPatterns("/*");
    registration.setName("requestBodySizeLimitFilter");
    return registration;
  }

  /**
   * 链路追踪过滤器
   *
   * <p>生成或提取 traceId，注入 MDC 和 RequestContext。 执行顺序：HIGH_PRECEDENCE + 10
   *
   * @return FilterRegistrationBean
   */
  @Bean
  @ConditionalOnMissingBean(name = "traceFilter")
  @ConditionalOnProperty(
      prefix = "ydsz.base.trace",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FilterRegistrationBean<TraceFilter> traceFilter() {
    FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new TraceFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    registration.addUrlPatterns("/*");
    registration.setName("traceFilter");
    return registration;
  }

  /**
   * 安全响应头过滤器（base 模块兜底实现）
   *
   * <p>添加安全相关的 HTTP 响应头，防止常见安全漏洞。 执行顺序：{@link FilterOrder#SECURITY_HEADER_FILTER}。
   *
   * <p><b>与 web/app/safe 模块的关系：</b> Bean 名统一为 {@code securityHeaderFilter}，通过
   * {@code @ConditionalOnMissingBean} 保证： 当项目中已存在 web/app/safe 模块注册的同名安全头过滤器时，本兜底实现自动退出，避免重复注册。
   *
   * @param properties 安全响应头配置属性
   * @return FilterRegistrationBean
   */
  @Bean
  @ConditionalOnMissingBean(name = "securityHeaderFilter")
  @ConditionalOnProperty(
      prefix = "ydsz.base.security-headers",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FilterRegistrationBean<SecurityHeadersFilter> securityHeaderFilter(
      BaseSecurityHeadersProperties properties) {
    FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new SecurityHeadersFilter(properties));
    registration.setOrder(FilterOrder.SECURITY_HEADER_FILTER);
    registration.addUrlPatterns("/*");
    registration.setName("securityHeaderFilter");
    return registration;
  }

  /**
   * RequestContext 清理过滤器
   *
   * <p>确保每个 HTTP 请求结束后自动清理 RequestContext，防止 ThreadLocal 内存泄漏。 该过滤器以 {@link
   * Ordered#LOWEST_PRECEDENCE} 优先级注册，保证在业务逻辑执行完毕后再清理。
   *
   * @return FilterRegistrationBean
   */
  @Bean
  @ConditionalOnMissingBean(name = "requestContextCleanupFilter")
  public FilterRegistrationBean<RequestContextCleanupFilter> requestContextCleanupFilter() {
    FilterRegistrationBean<RequestContextCleanupFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new RequestContextCleanupFilter());
    registration.setOrder(Ordered.LOWEST_PRECEDENCE);
    registration.addUrlPatterns("/*");
    registration.setName("requestContextCleanupFilter");
    return registration;
  }

  /**
   * Base 模块健康指标
   *
   * <p>报告时区、安全响应头、文档功能等基础配置的运行状态。 仅在 classpath 中存在 {@code HealthIndicator} 类时激活。
   *
   * @param securityHeadersProperties 安全响应头配置
   * @param docProperties 文档配置
   * @return BaseHealthIndicator 实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  public BaseHealthIndicator baseHealthIndicator(
      BaseSecurityHeadersProperties securityHeadersProperties,
      DocProperties docProperties,
      Environment environment) {
    String timezone = environment.getProperty("ydsz.base.timezone", "Asia/Shanghai");
    return new BaseHealthIndicator(securityHeadersProperties, docProperties, timezone);
  }

  /**
   * Core 模块健康指标（从 CoreAutoConfiguration 迁出，L6 层）。
   *
   * <p>TraceId 生成探针 + i18n 解析器状态检查。
   */
  @Bean
  @ConditionalOnMissingBean(name = "coreHealthIndicator")
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  public CoreHealthIndicator coreHealthIndicator() {
    return new CoreHealthIndicator();
  }
}
