package com.njydsz.common.web.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *   <li>{@link RequestLogInterceptor}（order=MIN_VALUE）- 请求日志记录</li>
 *   <li>{@link BaseHttpInterceptor}（order=MAX_VALUE）- 请求结束清理（RequestContext）</li>
 * </ul>
 *
 * @author ydsz-team
 * @see BaseMvcConfiguration
 * @see WebAuthFilter
 * @see RequestLogInterceptor
 * @see WebMetrics
 * @see WebHealthIndicator
 */
@AutoConfiguration
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
    private final String applicationName;

    public WebMvcConfiguration(WebCorsProperties webCorsProperties,
                               BaseHttpInterceptor baseHttpInterceptor,
                               AuthFilterConfiguration authFilterConfiguration,
                               AuthHandlerFactory authHandlerFactory,
                               WebTraceProperties webTraceProperties,
                               WebContentCacheProperties contentCacheProperties,
                               @Nullable AuthenticationProvider authenticationProvider,
                               ApplicationContext applicationContext) {
        super(webCorsProperties);
        this.baseHttpInterceptor = baseHttpInterceptor;
        this.authFilterConfiguration = authFilterConfiguration;
        this.authHandlerFactory = authHandlerFactory;
        this.webTraceProperties = webTraceProperties;
        this.contentCacheProperties = contentCacheProperties;
        this.authenticationProvider = authenticationProvider;
        this.applicationName = applicationContext.getApplicationName();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLogInterceptor())
                .addPathPatterns("/**")
                .order(BaseFilterOrders.INTERCEPTOR_REQUEST_LOG);

        registry.addInterceptor(baseHttpInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.REQUEST_CONTEXT_CLEANUP);
    }

    @Bean
    @ConditionalOnMissingBean(RequestLogInterceptor.class)
    public RequestLogInterceptor requestLogInterceptor() {
        return new RequestLogInterceptor(webTraceProperties);
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
    public FilterRegistrationBean<WebAuthFilter> authFilter() {
        WebAuthFilter authFilter = new WebAuthFilter(
                applicationName,
                authFilterConfiguration,
                authHandlerFactory,
                authenticationProvider
        );
        FilterRegistrationBean<WebAuthFilter> authFilterBean = new FilterRegistrationBean<>(authFilter);
        authFilterBean.addUrlPatterns("/*");
        authFilterBean.setName("webAuthFilter");
        authFilterBean.setOrder(BaseFilterOrders.AUTH_FILTER);
        return authFilterBean;
    }

    @Bean
    @ConditionalOnMissingBean(name = "securityHeaderFilter")
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
    public WebHealthIndicator webHealthIndicator(WebCorsProperties corsProperties,
                                                 WebTraceProperties traceProperties,
                                                 ObjectProvider<UserAgentAnalyzer> userAgentAnalyzerProvider,
                                                 ApplicationContext applicationContext) {
        boolean sessionRedisEnabled = isSessionRedisEnabled(applicationContext);
        boolean securityEnabled = isSecurityEnabled(applicationContext);
        return new WebHealthIndicator(corsProperties, traceProperties, userAgentAnalyzerProvider,
                sessionRedisEnabled, securityEnabled);
    }

    private boolean isSessionRedisEnabled(ApplicationContext context) {
        try {
            return context.containsBean("sessionRepositoryFilter")
                    || context.containsBean("redisHttpSessionConfiguration");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSecurityEnabled(ApplicationContext context) {
        try {
            return context.containsBean("securityFilterChain");
        } catch (Exception e) {
            return false;
        }
    }
}
