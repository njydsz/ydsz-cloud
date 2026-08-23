package com.njydsz.common.web.config;

import io.micrometer.core.instrument.MeterRegistry;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import com.njydsz.common.auth.config.AuthFilterConfiguration;
import com.njydsz.common.base.config.BaseAutoConfiguration;
import com.njydsz.common.base.config.BaseMvcConfiguration;
import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.common.base.constant.InterceptorOrder;
import com.njydsz.common.safe.config.SafeConfiguration;
import com.njydsz.common.safe.config.SecurityHeaderProperties;
import com.njydsz.common.web.advice.GlobalResponseAdvice;
import com.njydsz.common.web.auth.AuthHandlerFactory;
import com.njydsz.common.web.constant.WebFilterOrder;
import com.njydsz.common.web.filter.ContentCachingFilter;
import com.njydsz.common.web.filter.SecurityHeaderFilter;
import com.njydsz.common.web.filter.TraceIdResponseFilter;
import com.njydsz.common.web.filter.WebAuthFilter;
import com.njydsz.common.web.health.WebHealthIndicator;
import com.njydsz.common.web.interceptor.RequestLogInterceptor;
import com.njydsz.common.web.metrics.WebMetrics;

/**
 * Web 端 MVC 核心配置。
 *
 * <p>继承 {@link BaseMvcConfiguration}，注册 Web 端专属的拦截器和过滤器链：
 *
 * <p>ContentCachingFilter、WebAuthFilter、SecurityHeaderFilter、TraceIdResponseFilter、RequestLogInterceptor。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnPlatform(PlatformMode.WEB)
@AutoConfigureBefore({BaseAutoConfiguration.class, SafeConfiguration.class})
@EnableConfigurationProperties({WebContentCacheProperties.class})
public class WebMvcConfiguration extends BaseMvcConfiguration {

  private final AuthFilterConfiguration authFilterConfiguration;
  private final AuthHandlerFactory authHandlerFactory;
  private final WebTraceProperties webTraceProperties;
  private final WebContentCacheProperties contentCacheProperties;
  private final WebCorsProperties webCorsProperties;
  private final String applicationName;

  private final RequestLogInterceptor requestLogInterceptor;

  public WebMvcConfiguration(
      WebCorsProperties webCorsProperties,
      AuthFilterConfiguration authFilterConfiguration,
      AuthHandlerFactory authHandlerFactory,
      WebTraceProperties webTraceProperties,
      WebContentCacheProperties contentCacheProperties,
      RequestLogInterceptor requestLogInterceptor,
      ApplicationContext applicationContext) {
    super(webCorsProperties);
    this.webCorsProperties = webCorsProperties;
    this.authFilterConfiguration = authFilterConfiguration;
    this.authHandlerFactory = authHandlerFactory;
    this.webTraceProperties = webTraceProperties;
    this.contentCacheProperties = contentCacheProperties;
    this.requestLogInterceptor = requestLogInterceptor;
    this.applicationName = applicationContext.getApplicationName();
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(requestLogInterceptor)
        .addPathPatterns("/**")
        .order(InterceptorOrder.REQUEST_LOG);
  }

  /**
   * 注册请求日志拦截器（委托 WebMetrics，可选）。
   *
   * <p>记录请求耗时与链路信息，{@code WebMetrics} 通过 {@code ObjectProvider} 惰性获取，缺失时降级不采集。
   * {@code @ConditionalOnMissingBean} 允许自定义覆盖。
   *
   * @param webMetricsProvider Web 指标采集器（可选）
   * @return 请求日志拦截器
   */
  @Bean
  @ConditionalOnMissingBean(RequestLogInterceptor.class)
  public RequestLogInterceptor requestLogInterceptor(
      ObjectProvider<WebMetrics> webMetricsProvider) {
    return new RequestLogInterceptor(webTraceProperties, webMetricsProvider.getIfAvailable());
  }

  /**
   * 注册全局响应统一封装切面（@ControllerAdvice）。
   *
   * <p>将 Controller 返回值统一包装为标准响应体。{@code @ConditionalOnMissingBean} 允许自定义覆盖。
   *
   * @return 全局响应切面
   */
  @Bean
  @ConditionalOnMissingBean(GlobalResponseAdvice.class)
  public GlobalResponseAdvice globalResponseAdvice() {
    return new GlobalResponseAdvice();
  }

  /**
   * 注册请求体缓存过滤器（供后续组件多次读取 request body）。
   *
   * <p>优先级由 {@code FilterOrder.CONTENT_CACHING_FILTER} 决定；基于 {@code WebContentCacheProperties}
   * 控制缓存上限。 {@code @ConditionalOnMissingBean(name)} 允许外部以同名 Bean 覆盖。
   *
   * @return 内容缓存过滤器注册 Bean
   */
  @Bean
  @ConditionalOnMissingBean(name = "contentCachingFilter")
  public FilterRegistrationBean<ContentCachingFilter> contentCachingFilter() {
    FilterRegistrationBean<ContentCachingFilter> bean =
        new FilterRegistrationBean<>(new ContentCachingFilter(contentCacheProperties));
    bean.addUrlPatterns("/*");
    bean.setName("contentCachingFilter");
    bean.setOrder(WebFilterOrder.CONTENT_CACHING_FILTER);
    return bean;
  }

  /**
   * 注册 Web 认证过滤器（核心鉴权入口）。
   *
   * <p>组合应用名、认证过滤器配置、处理器工厂、认证提供方（可选）与指标（可选）构建 {@link WebAuthFilter}， 拦截 {@code /*}
   * 并对未认证请求按配置策略处理。{@code @ConditionalOnMissingBean(name)} 允许覆盖。
   *
   * @param webMetricsProvider Web 指标采集器（可选）
   * @return Web 认证过滤器注册 Bean
   */
  @Bean
  @ConditionalOnMissingBean(name = "webAuthFilter")
  public FilterRegistrationBean<WebAuthFilter> authFilter(
      ObjectProvider<WebMetrics> webMetricsProvider) {
    WebAuthFilter authFilter =
        new WebAuthFilter(
            applicationName,
            authFilterConfiguration,
            authHandlerFactory,
            webMetricsProvider.getIfAvailable());
    FilterRegistrationBean<WebAuthFilter> authFilterBean = new FilterRegistrationBean<>(authFilter);
    authFilterBean.addUrlPatterns("/*");
    authFilterBean.setName("webAuthFilter");
    authFilterBean.setOrder(WebFilterOrder.AUTH_FILTER);
    return authFilterBean;
  }

  /**
   * 注册安全响应头过滤器（依赖 safe 模块配置）。
   *
   * <p>仅当 {@code SecurityHeaderProperties} 存在（safe 模块启用）且安全头开关开启时装配； 为响应追加 CSP/HSTS/X-Frame-Options
   * 等防护头。{@code @ConditionalOnMissingBean(name)} 允许覆盖。
   *
   * @param securityHeaderProperties 安全响应头配置
   * @return 安全头过滤器注册 Bean
   */
  @Bean
  @ConditionalOnMissingBean(name = "securityHeaderFilter")
  @ConditionalOnBean(SecurityHeaderProperties.class)
  @ConditionalOnProperty(
      prefix = "ydsz.safe.security-headers",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FilterRegistrationBean<SecurityHeaderFilter> securityHeaderFilter(
      SecurityHeaderProperties securityHeaderProperties) {
    SecurityHeaderFilter securityHeaderFilter = new SecurityHeaderFilter(securityHeaderProperties);
    FilterRegistrationBean<SecurityHeaderFilter> bean =
        new FilterRegistrationBean<>(securityHeaderFilter);
    bean.addUrlPatterns("/*");
    bean.setName("securityHeaderFilter");
    bean.setOrder(com.njydsz.common.base.constant.FilterOrder.SECURITY_HEADER_FILTER);
    return bean;
  }

  /**
   * 注册 TraceId 响应过滤器。
   *
   * <p>将链路追踪 ID 注入响应头/MDC，便于日志串联；仅当 {@code ydsz.web.trace.enabled=true}（默认）时装配。
   * {@code @ConditionalOnMissingBean(name)} 允许覆盖。
   *
   * @return TraceId 过滤器注册 Bean
   */
  @Bean
  @ConditionalOnMissingBean(name = "traceIdResponseFilter")
  @ConditionalOnProperty(
      prefix = "ydsz.web.trace",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter() {
    TraceIdResponseFilter traceIdResponseFilter = new TraceIdResponseFilter(webTraceProperties);
    FilterRegistrationBean<TraceIdResponseFilter> bean =
        new FilterRegistrationBean<>(traceIdResponseFilter);
    bean.addUrlPatterns("/*");
    bean.setName("traceIdResponseFilter");
    bean.setOrder(WebFilterOrder.TRACE_ID_RESPONSE_FILTER);
    return bean;
  }

  /**
   * 注册 Web 层指标采集器。
   *
   * <p>采集请求计数/耗时等 Micrometer 指标，依赖 {@code MeterRegistry}（可选依赖不存在时不装配）。
   * {@code @ConditionalOnMissingBean} 允许自定义覆盖。
   *
   * @param meterRegistry Micrometer 注册中心
   * @return Web 指标采集器
   */
  @Bean
  @ConditionalOnMissingBean(WebMetrics.class)
  @ConditionalOnClass(MeterRegistry.class)
  public WebMetrics webMetrics(MeterRegistry meterRegistry) {
    return new WebMetrics(meterRegistry);
  }

  /**
   * 注册 Web 健康指示器。
   *
   * <p>综合 CORS/链路追踪/UA 解析、Redis Session 与安全是否启用（运行时反射探测 Bean 存在性）汇报健康状态； 仅当 Spring Boot Health
   * 抽象与开关 {@code ydsz.web.health-indicator.enabled=true}（默认）存在时装配。
   *
   * @param userAgentAnalyzerProvider UA 解析器（可选）
   * @param applicationContext 应用上下文（用于反射探测依赖）
   * @return Web 健康指示器
   */
  @Bean
  @ConditionalOnMissingBean(name = "webHealthIndicator")
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  @ConditionalOnProperty(
      prefix = "ydsz.web.health-indicator",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public WebHealthIndicator webHealthIndicator(
      ObjectProvider<UserAgentAnalyzer> userAgentAnalyzerProvider,
      ApplicationContext applicationContext) {
    boolean sessionRedisEnabled = isSessionRedisEnabled(applicationContext);
    boolean securityEnabled = isSecurityEnabled(applicationContext);
    return new WebHealthIndicator(
        webCorsProperties,
        webTraceProperties,
        userAgentAnalyzerProvider,
        sessionRedisEnabled,
        securityEnabled);
  }

  private boolean isSessionRedisEnabled(ApplicationContext context) {
    try {
            // CHECKSTYLE.OFF: RegexpSinglelineJava — 反射类名字符串常量，非代码引用
      Class<?> repoClass = Class.forName("org.springframework.session.SessionRepository");
      // CHECKSTYLE.ON: RegexpSinglelineJava
      String[] names = context.getBeanNamesForType(repoClass, false, false);
      return names.length > 0;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private boolean isSecurityEnabled(ApplicationContext context) {
    try {
            // CHECKSTYLE.OFF: RegexpSinglelineJava — 反射类名字符串常量，非代码引用
      Class<?> chainClass = Class.forName("org.springframework.security.web.SecurityFilterChain");
      // CHECKSTYLE.ON: RegexpSinglelineJava
      String[] names = context.getBeanNamesForType(chainClass, false, false);
      return names.length > 0;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
