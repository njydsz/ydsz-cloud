package com.njydsz.common.app.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import com.njydsz.common.app.advice.AppGlobalResponseAdvice;
import com.njydsz.common.app.exception.AppExceptionHandler;
import com.njydsz.common.app.filter.AppAuthFilter;
import com.njydsz.common.app.filter.AppContentCachingFilter;
import com.njydsz.common.app.filter.AppRequestIdResponseFilter;
import com.njydsz.common.app.filter.AppSecurityHeaderFilter;
import com.njydsz.common.app.filter.AppSignatureFilter;
import com.njydsz.common.app.health.AppHealthIndicator;
import com.njydsz.common.app.interceptor.AppRequestLogInterceptor;
import com.njydsz.common.app.metrics.AppMetrics;
import com.njydsz.common.auth.config.AuthFilterConfiguration;
import com.njydsz.common.auth.handler.AuthHandler;
import com.njydsz.common.auth.handler.AbstractAuthHandler;
import com.njydsz.common.auth.model.AuthenticationProvider;
import com.njydsz.common.base.config.BaseAutoConfiguration;
import com.njydsz.common.base.config.BaseMvcConfiguration;
import com.njydsz.common.base.constant.BaseFilterOrders;
import com.njydsz.common.base.interceptor.BaseHttpInterceptor;
import com.njydsz.common.safe.config.SafeConfiguration;
import com.njydsz.common.safe.config.SecurityHeaderProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * App 端 MVC 核心配置
 *
 * <p>App 端的核心 Web 配置，集中注册过滤器链、拦截器、健康检查、指标采集等。
 *
 * <p><b>Filter 链顺序（Order 由小到大）：</b>
 * <pre>
 * AppContentCachingFilter → AppSignatureFilter → AppSecurityHeaderFilter
 *                         → AppRequestIdResponseFilter → AppAuthFilter
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@AutoConfigureBefore({BaseAutoConfiguration.class, SafeConfiguration.class})
@EnableConfigurationProperties({AppCorsProperties.class, AppTraceProperties.class,
        AppSignatureProperties.class, AppContentCacheProperties.class})
@Import({AppGlobalResponseAdvice.class, AppExceptionHandler.class})
public class AppMvcConfiguration extends BaseMvcConfiguration {

    private final BaseHttpInterceptor baseHttpInterceptor;
    private final AppRequestLogInterceptor appRequestLogInterceptor;
    private final AppTraceProperties appTraceProperties;
    private final AppSignatureProperties appSignatureProperties;
    private final AppContentCacheProperties appContentCacheProperties;

    /**
     * 构造方法
     *
     * @param appCorsProperties        App 端 CORS 配置属性
     * @param baseHttpInterceptor      基础 HTTP 拦截器（请求上下文清理）
     * @param appRequestLogInterceptor App 端请求日志拦截器
     * @param appTraceProperties       App 端 Trace / 请求追踪配置
     * @param appSignatureProperties   App 端请求签名配置
     * @param appContentCacheProperties App 端请求体缓存配置
     */
    public AppMvcConfiguration(AppCorsProperties appCorsProperties,
                               BaseHttpInterceptor baseHttpInterceptor,
                               AppRequestLogInterceptor appRequestLogInterceptor,
                               AppTraceProperties appTraceProperties,
                               AppSignatureProperties appSignatureProperties,
                               AppContentCacheProperties appContentCacheProperties) {
        super(appCorsProperties);
        this.baseHttpInterceptor = baseHttpInterceptor;
        this.appRequestLogInterceptor = appRequestLogInterceptor;
        this.appTraceProperties = appTraceProperties;
        this.appSignatureProperties = appSignatureProperties;
        this.appContentCacheProperties = appContentCacheProperties;
    }

    /**
     * 注册拦截器
     *
     * @param registry Spring MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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
     * @return FilterRegistrationBean 实例
     */
    @Bean
    public FilterRegistrationBean<AppContentCachingFilter> appContentCachingFilter() {
        FilterRegistrationBean<AppContentCachingFilter> bean = new FilterRegistrationBean<>(
                new AppContentCachingFilter(appContentCacheProperties));
        bean.addUrlPatterns("/*");
        bean.setName("appContentCachingFilter");
        bean.setOrder(BaseFilterOrders.CONTENT_CACHING_FILTER);
        return bean;
    }

    /**
     * 注册 App 端请求签名校验过滤器
     *
     * <p>仅在 {@code ydsz.app.signature.enabled=true} 时激活。
     * 启用时必须配置至少一个密钥，否则启动失败。
     *
     * @param redisTemplateProvider Redis 模板（可选，用于 Nonce 防重放）
     * @param appMetricsProvider    App 指标采集器（可选）
     * @return FilterRegistrationBean 实例
     * @throws IllegalStateException 当启用签名校验但未配置任何密钥时抛出
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.app.signature", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<AppSignatureFilter> appSignatureFilter(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<AppMetrics> appMetricsProvider) {
        if (!appSignatureProperties.hasAnySecretConfigured()) {
            throw new IllegalStateException(
                    "启用签名验证时必须配置 ydsz.app.signature.app-secret 或 ydsz.app.signature.app-secrets");
        }
        AppSignatureFilter filter = new AppSignatureFilter(
                appSignatureProperties,
                redisTemplateProvider.getIfAvailable(),
                appMetricsProvider.getIfAvailable());
        FilterRegistrationBean<AppSignatureFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setName("appSignatureFilter");
        bean.setOrder(appSignatureProperties.getOrder());
        return bean;
    }

    /**
     * 创建 App 端默认认证处理器 Bean
     *
     * <p>业务方可通过提供自定义 {@link AbstractAuthHandler} 子类覆盖此默认实现。
     *
     * @return AppAuthHandler 实例
     */
    @Bean("appAuthHandler")
    @ConditionalOnMissingBean(name = "appAuthHandler")
    public AuthHandler appAuthHandler() {
        return new com.njydsz.common.app.auth.AppAuthHandler();
    }

    /**
     * 创建 App 端鉴权过滤器 Bean
     *
     * @param appAuthHandler             App 端认证处理器
     * @param authFilterConfiguration    通用鉴权过滤器配置
     * @param authenticationProvider     自定义认证提供者（可为空）
     * @param applicationContext         Spring 应用上下文
     * @return AppAuthFilter 实例
     */
    @Bean
    @ConditionalOnMissingBean(name = "appAuthFilter")
    public AppAuthFilter appAuthFilter(AuthHandler appAuthHandler,
                                        AuthFilterConfiguration authFilterConfiguration,
                                        @Nullable AuthenticationProvider authenticationProvider,
                                        ApplicationContext applicationContext) {
        String applicationName = applicationContext.getApplicationName();
        return new AppAuthFilter(applicationName, authFilterConfiguration, appAuthHandler, authenticationProvider);
    }

    /**
     * 注册鉴权过滤器到 Servlet 容器
     *
     * @param appAuthFilter App 端鉴权过滤器实例
     * @return FilterRegistrationBean 实例
     */
    @Bean
    public FilterRegistrationBean<AppAuthFilter> authFilter(AppAuthFilter appAuthFilter) {
        FilterRegistrationBean<AppAuthFilter> authFilterBean = new FilterRegistrationBean<>(appAuthFilter);
        authFilterBean.addUrlPatterns("/*");
        authFilterBean.setName("appAuthFilter");
        authFilterBean.setOrder(BaseFilterOrders.AUTH_FILTER);
        return authFilterBean;
    }

    /**
     * 注册安全响应头过滤器
     *
     * @param securityHeaderProperties 安全响应头配置
     * @return FilterRegistrationBean 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.safe.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AppSecurityHeaderFilter> securityHeaderFilter(SecurityHeaderProperties securityHeaderProperties) {
        AppSecurityHeaderFilter securityHeaderFilter = new AppSecurityHeaderFilter(securityHeaderProperties);
        FilterRegistrationBean<AppSecurityHeaderFilter> bean = new FilterRegistrationBean<>(securityHeaderFilter);
        bean.addUrlPatterns("/*");
        bean.setName("securityHeaderFilter");
        bean.setOrder(BaseFilterOrders.SECURITY_HEADER_FILTER);
        return bean;
    }

    /**
     * 注册请求 ID 响应过滤器
     *
     * @return FilterRegistrationBean 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.app.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AppRequestIdResponseFilter> requestIdResponseFilter() {
        AppRequestIdResponseFilter requestIdResponseFilter = new AppRequestIdResponseFilter(appTraceProperties);
        FilterRegistrationBean<AppRequestIdResponseFilter> bean = new FilterRegistrationBean<>(requestIdResponseFilter);
        bean.addUrlPatterns("/*");
        bean.setName("appRequestIdResponseFilter");
        bean.setOrder(BaseFilterOrders.TRACE_ID_RESPONSE_FILTER);
        return bean;
    }

    /**
     * 注册 App 指标采集器 Bean
     *
     * @param meterRegistryProvider Micrometer MeterRegistry（可选）
     * @return AppMetrics 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AppMetrics appMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new AppMetrics(meterRegistryProvider.getIfAvailable());
    }

    /**
     * 注册 App 健康检查指示器 Bean
     *
     * @param redisTemplateProvider Redis 模板（可选）
     * @param appMetricsProvider    App 指标采集器（可选）
     * @return AppHealthIndicator 实例
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "ydsz.app", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AppHealthIndicator appHealthIndicator(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                                   ObjectProvider<AppMetrics> appMetricsProvider) {
        return new AppHealthIndicator(appSignatureProperties, redisTemplateProvider, appMetricsProvider);
    }
}
