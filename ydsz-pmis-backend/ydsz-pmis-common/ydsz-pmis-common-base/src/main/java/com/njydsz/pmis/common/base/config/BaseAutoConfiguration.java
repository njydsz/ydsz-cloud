package com.njydsz.pmis.common.base.config;

import com.njydsz.pmis.common.base.constant.BaseFilterOrders;
import com.njydsz.pmis.common.base.filter.RequestContextCleanupFilter;
import com.njydsz.pmis.common.base.filter.SecurityHeadersFilter;
import com.njydsz.pmis.common.base.filter.TraceFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Base 模块自动配置
 *
 * <p>提供 Web/App 公共基座层的自动装配能力，包括：
 * <ul>
 *   <li>RequestContext 清理过滤器</li>
 *   <li>链路追踪过滤器（TraceFilter）</li>
 *   <li>安全响应头过滤器（SecurityHeadersFilter）</li>
 *   <li>CORS 跨域配置属性绑定</li>
 *   <li>Trace 追踪配置属性绑定</li>
 * </ul>
 *
 * <p>注意：BaseCorsProperties 和 BaseTraceProperties 为抽象基类，
 * 实际配置由 Web/App 子模块通过 {@code @ConfigurationProperties} 注解提供具体前缀。
 * 若业务方直接使用 base 模块，请继承这些基类并指定自己的前缀。
 *
 * <p>横切点执行顺序参考 {@code docs/BASE_INTERCEPTOR_ORDER.md}。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "ydsz.base", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BaseSecurityHeadersProperties.class)
public class BaseAutoConfiguration {

    /**
     * 链路追踪过滤器
     *
     * <p>生成或提取 traceId，注入 MDC 和 RequestContext。
     * 执行顺序：HIGH_PRECEDENCE + 10
     *
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnMissingBean(name = "traceFilter")
    @ConditionalOnProperty(prefix = "ydsz.base.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
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
     * <p>添加安全相关的 HTTP 响应头，防止常见安全漏洞。
     * 执行顺序：{@link BaseFilterOrders#SECURITY_HEADER_FILTER}。
     *
     * <p><b>与 web/app/safe 模块的关系：</b>
     * Bean 名统一为 {@code securityHeaderFilter}，通过 {@code @ConditionalOnMissingBean} 保证：
     * 当项目中已存在 web/app/safe 模块注册的同名安全头过滤器时，本兜底实现自动退出，避免重复注册。
     *
     * @param properties 安全响应头配置属性
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnMissingBean(name = "securityHeaderFilter")
    @ConditionalOnProperty(prefix = "ydsz.base.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeaderFilter(BaseSecurityHeadersProperties properties) {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeadersFilter(properties));
        registration.setOrder(BaseFilterOrders.SECURITY_HEADER_FILTER);
        registration.addUrlPatterns("/*");
        registration.setName("securityHeaderFilter");
        return registration;
    }

    /**
     * RequestContext 清理过滤器
     *
     * <p>确保每个 HTTP 请求结束后自动清理 RequestContext，防止 ThreadLocal 内存泄漏。
     * 该过滤器以 {@link Ordered#LOWEST_PRECEDENCE} 优先级注册，保证在业务逻辑执行完毕后再清理。
     *
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnMissingBean(name = "requestContextCleanupFilter")
    public FilterRegistrationBean<RequestContextCleanupFilter> requestContextCleanupFilter() {
        FilterRegistrationBean<RequestContextCleanupFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestContextCleanupFilter());
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("requestContextCleanupFilter");
        return registration;
    }
}