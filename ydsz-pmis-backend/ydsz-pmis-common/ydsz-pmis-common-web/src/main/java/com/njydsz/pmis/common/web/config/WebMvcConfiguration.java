package com.njydsz.pmis.common.web.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import com.njydsz.pmis.common.auth.config.AuthFilterConfiguration;
import com.njydsz.pmis.common.auth.model.AuthenticationProvider;
import com.njydsz.pmis.common.base.config.BaseAutoConfiguration;
import com.njydsz.pmis.common.base.config.BaseMvcConfiguration;
import com.njydsz.pmis.common.base.constant.BaseFilterOrders;
import com.njydsz.pmis.common.base.interceptor.BaseHttpInterceptor;
import com.njydsz.pmis.common.safe.config.SafeConfiguration;
import com.njydsz.pmis.common.safe.config.SecurityHeaderProperties;
import com.njydsz.pmis.common.web.advice.GlobalResponseAdvice;
import com.njydsz.pmis.common.web.auth.AuthHandlerFactory;
import com.njydsz.pmis.common.web.exception.WebExceptionHandler;
import com.njydsz.pmis.common.web.filter.ContentCachingFilter;
import com.njydsz.pmis.common.web.filter.SecurityHeaderFilter;
import com.njydsz.pmis.common.web.filter.TraceIdResponseFilter;
import com.njydsz.pmis.common.web.filter.WebAuthFilter;
import com.njydsz.pmis.common.web.interceptor.RequestLogInterceptor;

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
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseMvcConfiguration
 * @see WebAuthFilter
 * @see RequestLogInterceptor
 */
@AutoConfiguration
@AutoConfigureBefore({BaseAutoConfiguration.class, SafeConfiguration.class})
@EnableConfigurationProperties({WebCorsProperties.class, WebTraceProperties.class})
@Import({GlobalResponseAdvice.class, WebExceptionHandler.class})
public class WebMvcConfiguration extends BaseMvcConfiguration {

    private final BaseHttpInterceptor baseHttpInterceptor;
    private final RequestLogInterceptor requestLogInterceptor;
    @Nullable
    private final AuthenticationProvider authenticationProvider;
    private final AuthFilterConfiguration authFilterConfiguration;
    private final AuthHandlerFactory authHandlerFactory;
    private final WebTraceProperties webTraceProperties;
    private final String applicationName;

    public WebMvcConfiguration(WebCorsProperties webCorsProperties,
                               SecurityHeaderProperties securityHeaderProperties,
                               BaseHttpInterceptor baseHttpInterceptor,
                               RequestLogInterceptor requestLogInterceptor,
                               AuthFilterConfiguration authFilterConfiguration,
                               AuthHandlerFactory authHandlerFactory,
                               WebTraceProperties webTraceProperties,
                               @Nullable AuthenticationProvider authenticationProvider,
                               ApplicationContext applicationContext) {
        super(webCorsProperties);
        this.baseHttpInterceptor = baseHttpInterceptor;
        this.requestLogInterceptor = requestLogInterceptor;
        this.authFilterConfiguration = authFilterConfiguration;
        this.authHandlerFactory = authHandlerFactory;
        this.webTraceProperties = webTraceProperties;
        this.authenticationProvider = authenticationProvider;
        this.applicationName = applicationContext.getApplicationName();
    }

    protected void configureObjectMapper(ObjectMapper mapper) {
        // Web端大数格式化：避免科学计数法
        mapper.configure(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN.mappedFeature(), true);
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
    public FilterRegistrationBean<ContentCachingFilter> contentCachingFilter() {
        FilterRegistrationBean<ContentCachingFilter> bean = new FilterRegistrationBean<>(new ContentCachingFilter());
        bean.addUrlPatterns("/*");
        bean.setName("contentCachingFilter");
        bean.setOrder(BaseFilterOrders.CONTENT_CACHING_FILTER);
        return bean;
    }

    @Bean
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
    @ConditionalOnProperty(prefix = "ydsz.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter() {
        TraceIdResponseFilter traceIdResponseFilter = new TraceIdResponseFilter(webTraceProperties);
        FilterRegistrationBean<TraceIdResponseFilter> bean = new FilterRegistrationBean<>(traceIdResponseFilter);
        bean.addUrlPatterns("/*");
        bean.setName("traceIdResponseFilter");
        bean.setOrder(BaseFilterOrders.TRACE_ID_RESPONSE_FILTER);
        return bean;
    }
}
