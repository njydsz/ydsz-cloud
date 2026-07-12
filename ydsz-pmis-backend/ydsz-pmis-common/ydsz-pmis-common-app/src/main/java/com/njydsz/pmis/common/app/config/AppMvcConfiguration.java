package com.njydsz.pmis.common.app.config;

import com.njydsz.pmis.common.app.advice.AppGlobalResponseAdvice;
import com.njydsz.pmis.common.app.filter.AppContentCachingFilter;
import com.njydsz.pmis.common.app.filter.AppRequestIdResponseFilter;
import com.njydsz.pmis.common.app.filter.AppSignatureFilter;
import com.njydsz.pmis.common.app.interceptor.AppRequestLogInterceptor;
import com.njydsz.pmis.common.base.config.BaseAutoConfiguration;
import com.njydsz.pmis.common.base.config.BaseMvcConfiguration;
import com.njydsz.pmis.common.base.constant.BaseFilterOrders;
import com.njydsz.pmis.common.base.interceptor.BaseHttpInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/**
 * App 端 MVC 核心配置
 *
 * <p>App 端的核心 Web 配置，集中注册过滤器链、拦截器、对象映射等。
 * 与 Web 端相比，App 端在 Filter 链、签名校验、CORS 等方面有以下差异：
 * <ul>
 *   <li>支持 {@link AppSignatureFilter} 请求签名校验（防止请求伪造）</li>
 *   <li>过滤器链顺序：内容缓存 → 安全头 → 签名 → TraceId 响应 → 鉴权</li>
 *   <li>拦截器顺序：请求日志 → 请求上下文清理</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
@AutoConfigureBefore(BaseAutoConfiguration.class)
@EnableConfigurationProperties({AppCorsProperties.class, AppTraceProperties.class, AppSignatureProperties.class})
@Import({AppGlobalResponseAdvice.class})
public class AppMvcConfiguration extends BaseMvcConfiguration {

    private final BaseHttpInterceptor baseHttpInterceptor;
    private final AppRequestLogInterceptor appRequestLogInterceptor;
    private final AppTraceProperties appTraceProperties;
    private final AppSignatureProperties appSignatureProperties;

    /**
     * 构造方法
     *
     * @param appCorsProperties       App 端 CORS 配置属性
     * @param baseHttpInterceptor     基础 HTTP 拦截器
     * @param appRequestLogInterceptor App 端请求日志拦截器
     * @param appTraceProperties      App 端 Trace 配置
     * @param appSignatureProperties  App 端签名配置
     */
    public AppMvcConfiguration(AppCorsProperties appCorsProperties,
                               BaseHttpInterceptor baseHttpInterceptor,
                               AppRequestLogInterceptor appRequestLogInterceptor,
                               AppTraceProperties appTraceProperties,
                               AppSignatureProperties appSignatureProperties) {
        super(appCorsProperties);
        this.baseHttpInterceptor = baseHttpInterceptor;
        this.appRequestLogInterceptor = appRequestLogInterceptor;
        this.appTraceProperties = appTraceProperties;
        this.appSignatureProperties = appSignatureProperties;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(appRequestLogInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.INTERCEPTOR_REQUEST_LOG);

        registry.addInterceptor(baseHttpInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.REQUEST_CONTEXT_CLEANUP);
    }

    /**
     * 注册请求体缓存过滤器
     *
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<AppContentCachingFilter> appContentCachingFilter() {
        FilterRegistrationBean<AppContentCachingFilter> bean = new FilterRegistrationBean<>(new AppContentCachingFilter());
        bean.addUrlPatterns("/*");
        bean.setName("appContentCachingFilter");
        bean.setOrder(BaseFilterOrders.CONTENT_CACHING_FILTER);
        return bean;
    }

    /**
     * 注册 App 端请求签名校验过滤器
     *
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnProperty(prefix = "pmis.app.signature", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<AppSignatureFilter> appSignatureFilter() {
        if (appSignatureProperties.getAppSecret() == null || appSignatureProperties.getAppSecret().isBlank()) {
            throw new IllegalStateException("启用签名验证时必须配置 pmis.app.signature.app-secret");
        }
        AppSignatureFilter filter = new AppSignatureFilter(
                appSignatureProperties.getAppSecret(),
                appSignatureProperties.getTimestampTolerance());
        FilterRegistrationBean<AppSignatureFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setName("appSignatureFilter");
        bean.setOrder(appSignatureProperties.getOrder());
        return bean;
    }

    /**
     * 注册请求 ID 响应过滤器
     *
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnProperty(prefix = "pmis.app.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AppRequestIdResponseFilter> appRequestIdResponseFilter() {
        AppRequestIdResponseFilter filter = new AppRequestIdResponseFilter(appTraceProperties);
        FilterRegistrationBean<AppRequestIdResponseFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setName("appRequestIdResponseFilter");
        bean.setOrder(BaseFilterOrders.TRACE_ID_RESPONSE_FILTER);
        return bean;
    }

    /**
     * 注册基础 HTTP 拦截器
     *
     * @return BaseHttpInterceptor
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public BaseHttpInterceptor baseHttpInterceptor() {
        return new BaseHttpInterceptor();
    }
}
