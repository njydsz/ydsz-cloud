package com.njydsz.common.web.config;

import org.jspecify.annotations.Nullable;
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
import com.njydsz.common.auth.model.AuthenticationProvider;
import com.njydsz.common.base.config.BaseAutoConfiguration;
import com.njydsz.common.base.config.BaseMvcConfiguration;
import com.njydsz.common.base.constant.BaseFilterOrders;
import com.njydsz.common.base.interceptor.BaseHttpInterceptor;
import com.njydsz.common.safe.config.SafeConfiguration;
import com.njydsz.common.safe.config.SecurityHeaderProperties;
import com.njydsz.common.web.advice.GlobalResponseAdvice;
import com.njydsz.common.web.auth.AuthHandlerFactory;
import com.njydsz.common.web.filter.ContentCachingFilter;
import com.njydsz.common.web.filter.SecurityHeaderFilter;
import com.njydsz.common.web.filter.TraceIdResponseFilter;
import com.njydsz.common.web.filter.WebAuthFilter;
import com.njydsz.common.web.health.WebHealthIndicator;
import com.njydsz.common.web.interceptor.RequestLogInterceptor;
import com.njydsz.common.web.metrics.WebMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import nl.basjes.parse.useragent.UserAgentAnalyzer;

/**
 * Web 端 MVC 核心配置
 *
 * <p>继承 {@link BaseMvcConfiguration}，注册 Web 端专属的拦截器和过滤器链。
 *
 * <p><b>过滤器链（按 order 从小到大执行）：</b>
 * <ol>
 *   <li>{@link ContentCachingFilter}（order=MIN_VALUE）- 请求体缓存，支持多次读取</li>
 *   <li>{@link WebAuthFilter}（order=3）- 认证鉴权，解析 Token 并构建上下文</li>
 *   <li>{@link SecurityHeaderFilter}（order=可配置）- 安全响应头注入</li>
 *   <li>{@link TraceIdResponseFilter}（order=5）- TraceId 写入响应头</li>
 * </ol>
 *
 * <p><b>拦截器链：</b>
 * <ul>
 *   <li>{@link RequestLogInterceptor}（order=MIN_VALUE）- 请求日志记录 + HTTP 请求指标埋点</li>
 *   <li>{@link BaseHttpInterceptor}（order=MAX_VALUE）- 请求结束清理（RequestContext）</li>
 * </ul>
 *
 * <p><b>异常处理：</b>由 {@code common-exception} 模块的 {@code MvcExceptionHandler} 统一处理，
 * 本模块不再注册独立的异常处理器，避免重复设计。
 *
 * @author ydsz-team
 * @see BaseMvcConfiguration
 * @see WebAuthFilter
 * @see RequestLogInterceptor
 * @see WebMetrics
 * @see WebHealthIndicator
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@AutoConfigureBefore({BaseAutoConfiguration.class, SafeConfiguration.class})
@EnableConfigurationProperties({
        WebCorsProperties.class, WebTraceProperties.class, WebContentCacheProperties.class
})
public class WebMvcConfiguration extends BaseMvcConfiguration {

    private final BaseHttpInterceptor baseHttpInterceptor;
    @Nullable
    private final AuthenticationProvider authenticationProvider;
    private final AuthFilterConfiguration authFilterConfiguration;
    private final AuthHandlerFactory authHandlerFactory;
    private final WebTraceProperties webTraceProperties;
    private final WebContentCacheProperties contentCacheProperties;
    private final WebCorsProperties webCorsProperties;
    private final String applicationName;

    private final RequestLogInterceptor requestLogInterceptor;

    public WebMvcConfiguration(WebCorsProperties webCorsProperties,
                               BaseHttpInterceptor baseHttpInterceptor,
                               AuthFilterConfiguration authFilterConfiguration,
                               AuthHandlerFactory authHandlerFactory,
                               WebTraceProperties webTraceProperties,
                               WebContentCacheProperties contentCacheProperties,
                               RequestLogInterceptor requestLogInterceptor,
                               @Nullable AuthenticationProvider authenticationProvider,
                               ApplicationContext applicationContext) {
        super(webCorsProperties);
        this.webCorsProperties = webCorsProperties;
        this.baseHttpInterceptor = baseHttpInterceptor;
        this.authFilterConfiguration = authFilterConfiguration;
        this.authHandlerFactory = authHandlerFactory;
        this.webTraceProperties = webTraceProperties;
        this.contentCacheProperties = contentCacheProperties;
        this.requestLogInterceptor = requestLogInterceptor;
        this.authenticationProvider = authenticationProvider;
        this.applicationName = applicationContext.getApplicationName();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLogInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.INTERCEPTOR_REQUEST_LOG);

        registry.addInterceptor(baseHttpInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.REQUEST_CONTEXT_CLEANUP);
    }

    @Bean
    @ConditionalOnMissingBean(RequestLogInterceptor.class)
    public RequestLogInterceptor requestLogInterceptor(ObjectProvider<WebMetrics> webMetricsProvider) {
        return new RequestLogInterceptor(webTraceProperties, webMetricsProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(GlobalResponseAdvice.class)
    public GlobalResponseAdvice globalResponseAdvice() {
        return new GlobalResponseAdvice();
    }

    @Bean
    @ConditionalOnMissingBean(name = "contentCachingFilter")
    public FilterRegistrationBean<ContentCachingFilter> contentCachingFilter() {
        FilterRegistrationBean<ContentCachingFilter> bean = new FilterRegistrationBean<>(
                new ContentCachingFilter(contentCacheProperties));
        bean.addUrlPatterns("/*");
        bean.setName("contentCachingFilter");
        bean.setOrder(BaseFilterOrders.CONTENT_CACHING_FILTER);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean(name = "webAuthFilter")
    public FilterRegistrationBean<WebAuthFilter> authFilter(ObjectProvider<WebMetrics> webMetricsProvider) {
        WebAuthFilter authFilter = new WebAuthFilter(
                applicationName,
                authFilterConfiguration,
                authHandlerFactory,
                authenticationProvider,
                webMetricsProvider.getIfAvailable()
        );
        FilterRegistrationBean<WebAuthFilter> authFilterBean = new FilterRegistrationBean<>(authFilter);
        authFilterBean.addUrlPatterns("/*");
        authFilterBean.setName("webAuthFilter");
        authFilterBean.setOrder(BaseFilterOrders.AUTH_FILTER);
        return authFilterBean;
    }

    @Bean
    @ConditionalOnMissingBean(name = "securityHeaderFilter")
    @ConditionalOnBean(SecurityHeaderProperties.class)
    @ConditionalOnProperty(prefix = "ydsz.safe.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SecurityHeaderFilter> securityHeaderFilter(SecurityHeaderProperties securityHeaderProperties) {
        SecurityHeaderFilter securityHeaderFilter = new SecurityHeaderFilter(securityHeaderProperties);
        FilterRegistrationBean<SecurityHeaderFilter> bean = new FilterRegistrationBean<>(securityHeaderFilter);
        bean.addUrlPatterns("/*");
        bean.setName("securityHeaderFilter");
        bean.setOrder(BaseFilterOrders.SECURITY_HEADER_FILTER);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean(name = "traceIdResponseFilter")
    @ConditionalOnProperty(prefix = "ydsz.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter() {
        TraceIdResponseFilter traceIdResponseFilter = new TraceIdResponseFilter(webTraceProperties);
        FilterRegistrationBean<TraceIdResponseFilter> bean = new FilterRegistrationBean<>(traceIdResponseFilter);
        bean.addUrlPatterns("/*");
        bean.setName("traceIdResponseFilter");
        bean.setOrder(BaseFilterOrders.TRACE_ID_RESPONSE_FILTER);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean(WebMetrics.class)
    @ConditionalOnClass(MeterRegistry.class)
    public WebMetrics webMetrics(MeterRegistry meterRegistry) {
        return new WebMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "webHealthIndicator")
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnProperty(prefix = "ydsz.web.health-indicator", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebHealthIndicator webHealthIndicator(
            ObjectProvider<UserAgentAnalyzer> userAgentAnalyzerProvider,
            ApplicationContext applicationContext) {
        boolean sessionRedisEnabled = isSessionRedisEnabled(applicationContext);
        boolean securityEnabled = isSecurityEnabled(applicationContext);
        return new WebHealthIndicator(webCorsProperties, webTraceProperties, userAgentAnalyzerProvider,
                sessionRedisEnabled, securityEnabled);
    }

    private boolean isSessionRedisEnabled(ApplicationContext context) {
        try {
            Class<?> repoClass = Class.forName("org.springframework.session.SessionRepository");
            String[] names = context.getBeanNamesForType(repoClass, false, false);
            return names.length > 0;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isSecurityEnabled(ApplicationContext context) {
        try {
            Class<?> chainClass = Class.forName("org.springframework.security.web.SecurityFilterChain");
            String[] names = context.getBeanNamesForType(chainClass, false, false);
            return names.length > 0;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
