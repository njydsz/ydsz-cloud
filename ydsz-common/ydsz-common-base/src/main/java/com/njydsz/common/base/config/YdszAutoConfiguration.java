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
import com.njydsz.common.base.filter.TraceFilter;
import com.njydsz.common.base.health.CoreHealthIndicator;
import com.njydsz.common.base.health.YdszHealthIndicator;

/**
 * Base 模块自动配置
 *
 * <p>提供 Web/App 公共基座层的自动装配能力，包括：
 *
 * <ul>
 *   <li>RequestContext 清理过滤器
 *   <li>链路追踪过滤器（TraceFilter）
 *   <li>健康指标（YdszHealthIndicator，需 actuator 依赖）
 * </ul>
 *
 * <p>注：安全响应头过滤器已下沉至 common-safe 模块统一管理，base 模块不再注册兜底实现。
 *
 * <p>注意：BaseCorsProperties 和 BaseTraceProperties 为抽象基类， 实际配置由 Web/App 子模块通过
 * {@code @ConfigurationProperties} 注解提供具体前缀。 若业务方直接使用 base 模块，请继承这些基类并指定自己的前缀。
 *
 * <p>文档相关健康指标由本模块内 {@code ydsz.common.base.config.DocAutoConfiguration} 提供。
 *
 * <p>横切点执行顺序参考 {@code docs/BASE_INTERCEPTOR_ORDER.md}。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(
    prefix = "ydsz.base",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(YdszRequestProperties.class)
public class YdszAutoConfiguration {

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
      YdszRequestProperties properties) {
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
   * <p>报告时区、文档功能等基础配置的运行状态。 仅在 classpath 中存在 {@code HealthIndicator} 类时激活。
   *
   * @param docProperties 文档配置
   * @return YdszHealthIndicator 实例
   */
  @Bean
  @ConditionalOnMissingBean
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  public YdszHealthIndicator baseHealthIndicator(
      DocProperties docProperties,
      Environment environment) {
    String timezone = environment.getProperty("ydsz.base.timezone", "Asia/Shanghai");
    return new YdszHealthIndicator(docProperties, timezone);
  }

  /**
   * Core 模块健康指标（从 CoreAutoConfiguration 迁出，L6 层）。
   *
   * <p>Ydsz-Core 提供的健康指标（如内存、CPU 等），仅在 base 模块引入时激活。
   *
   * @return CoreHealthIndicator 实例
   */
  @Bean
  @ConditionalOnMissingBean(name = "coreHealthIndicator")
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  public CoreHealthIndicator coreHealthIndicator() {
    return new CoreHealthIndicator();
  }

}
