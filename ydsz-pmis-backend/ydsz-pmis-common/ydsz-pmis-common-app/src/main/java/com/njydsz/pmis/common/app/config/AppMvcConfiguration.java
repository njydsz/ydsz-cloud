package com.njydsz.pmis.common.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.njydsz.pmis.common.app.advice.AppGlobalResponseAdvice;
import com.njydsz.pmis.common.app.exception.AppExceptionHandler;
import com.njydsz.pmis.common.app.filter.AppAuthFilter;
import com.njydsz.pmis.common.app.filter.AppContentCachingFilter;
import com.njydsz.pmis.common.app.filter.AppRequestIdResponseFilter;
import com.njydsz.pmis.common.app.filter.AppSecurityHeaderFilter;
import com.njydsz.pmis.common.app.filter.AppSignatureFilter;
import com.njydsz.pmis.common.app.interceptor.AppRequestLogInterceptor;
import com.njydsz.pmis.common.base.config.BaseAutoConfiguration;
import com.njydsz.pmis.common.base.constant.BaseFilterOrders;
import com.njydsz.pmis.common.base.interceptor.BaseHttpInterceptor;
import com.njydsz.pmis.common.auth.config.AuthFilterConfiguration;
import com.njydsz.pmis.common.auth.model.AuthenticationProvider;
import com.njydsz.pmis.common.base.config.BaseMvcConfiguration;
import com.njydsz.pmis.common.safe.config.SafeConfiguration;
import com.njydsz.pmis.common.safe.config.SecurityHeaderProperties;
import com.njydsz.pmis.common.auth.handler.AuthHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
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
 *   <li>全局 ObjectMapper 关闭 BigDecimal 科学计数法</li>
 * </ul>
 *
 * <p><b>Filter 链顺序（Order 由小到大）：</b>
 * <pre>
 * AppContentCachingFilter  → AppSecurityHeaderFilter → AppSignatureFilter
 *                          → AppRequestIdResponseFilter → AppAuthFilter
 * </pre>
 *
 * <p><b>线程安全性：</b>本配置类为无状态配置类，注册的 Filter / Interceptor 均为线程安全实现。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@AutoConfigureBefore({BaseAutoConfiguration.class, SafeConfiguration.class})
@EnableConfigurationProperties({AppCorsProperties.class, AppTraceProperties.class, AppSignatureProperties.class})
@Import({AppGlobalResponseAdvice.class, AppExceptionHandler.class})
public class AppMvcConfiguration extends BaseMvcConfiguration {

    private final BaseHttpInterceptor baseHttpInterceptor;
    private final AppRequestLogInterceptor appRequestLogInterceptor;
    private final AppTraceProperties appTraceProperties;
    private final AppSignatureProperties appSignatureProperties;

    /**
     * 构造方法
     *
     * @param appCorsProperties       App 端 CORS 配置属性
     * @param securityHeaderProperties 安全响应头配置属性
     * @param baseHttpInterceptor     基础 HTTP 拦截器（请求上下文清理）
     * @param appRequestLogInterceptor App 端请求日志拦截器
     * @param appTraceProperties      App 端 Trace / 请求追踪配置
     * @param appSignatureProperties  App 端请求签名配置
     */
    public AppMvcConfiguration(AppCorsProperties appCorsProperties,
                               SecurityHeaderProperties securityHeaderProperties,
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

    /**
     * 自定义 ObjectMapper 配置
     *
     * <p>App 端关闭 BigDecimal 的科学计数法表示，确保长 ID 等大数字以普通十进制字符串输出。
     *
     * @param mapper Spring MVC 默认 ObjectMapper 实例
     */
    @SuppressWarnings("deprecation")
    protected void configureObjectMapper(ObjectMapper mapper) {
        // App端大数格式化：避免科学计数法
        mapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true);
    }

    /**
     * 注册拦截器
     *
     * <p>注册顺序：
     * <ol>
     *   <li>App 端请求日志拦截器（顺序：{@link BaseFilterOrders#INTERCEPTOR_REQUEST_LOG}）</li>
     *   <li>基础请求上下文清理拦截器（顺序：{@link BaseFilterOrders#REQUEST_CONTEXT_CLEANUP}）</li>
     * </ol>
     *
     * @param registry Spring MVC 拦截器注册表
     */
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
     * <p>用于在过滤器链中后续节点可以重复读取请求体（如签名校验、参数校验）。
     *
     * @return FilterRegistrationBean 实例
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
     * <p>仅在 {@code remi.app.signature.enabled=true} 时激活。
     * 启用时必须配置 {@code remi.app.signature.app-secret}，否则启动失败。
     *
     * @return FilterRegistrationBean 实例
     * @throws IllegalStateException 当启用签名校验但未配置 app-secret 时抛出
     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.app.signature", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<AppSignatureFilter> appSignatureFilter() {
        if (appSignatureProperties.getAppSecret() == null || appSignatureProperties.getAppSecret().isBlank()) {
            throw new IllegalStateException("启用签名验证时必须配置 remi.app.signature.app-secret");
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
     * 创建 App 端鉴权过滤器 Bean
     *
     * <p>仅在业务方未自行注册 {@code appAuthFilter} 时创建默认实现。
     * 业务方可提供自定义 {@link com.njydsz.pmis.common.auth.model.AuthenticationProvider} 覆盖默认认证逻辑。
     *
     * @param appAuthHandler             App 端认证处理器
     * @param authFilterConfiguration    通用鉴权过滤器配置
     * @param authenticationProvider     自定义认证提供者（可为空，为空时使用 AuthHandler）
     * @param applicationContext         Spring 应用上下文，用于获取应用名
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
     * <p>默认开启（{@code remi.safe.security-headers.enabled=true}），通过 {@link SecurityHeaderProperties}
     * 控制具体响应头策略。
     *
     * @param securityHeaderProperties 安全响应头配置
     * @return FilterRegistrationBean 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.safe.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
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
     * <p>默认开启（{@code remi.app.trace.enabled=true}），将当前请求的 RequestId 写入响应头
     * {@code X-Request-Id}，便于客户端关联日志。
     *
     * @return FilterRegistrationBean 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.app.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AppRequestIdResponseFilter> requestIdResponseFilter() {
        AppRequestIdResponseFilter requestIdResponseFilter = new AppRequestIdResponseFilter(appTraceProperties);
        FilterRegistrationBean<AppRequestIdResponseFilter> bean = new FilterRegistrationBean<>(requestIdResponseFilter);
        bean.addUrlPatterns("/*");
        bean.setName("appRequestIdResponseFilter");
        bean.setOrder(BaseFilterOrders.TRACE_ID_RESPONSE_FILTER);
        return bean;
    }
}
