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
 * Base 模块自动装配
 *
 * <p>提供 Web/App 公共基座层的自动装配能力，包括：
 * <ul>
 *   <li>RequestContext 清理过滤器</li>
 *   <li>链路追踪过滤器（TraceFilter）</li>
 *   <li>安全响应头过滤器（SecurityHeadersFilter）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "pmis.base", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BaseSecurityHeadersProperties.class)
public class BaseAutoConfiguration {

    /**
     * 链路追踪过滤器
     *
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnMissingBean(name = "traceFilter")
    @ConditionalOnProperty(prefix = "pmis.base.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
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
     * @param properties 安全响应头配置属性
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnMissingBean(name = "securityHeaderFilter")
    @ConditionalOnProperty(prefix = "pmis.base.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
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
