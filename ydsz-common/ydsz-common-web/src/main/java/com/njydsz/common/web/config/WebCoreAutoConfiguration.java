package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import com.njydsz.common.core.config.CoreProperties;
import com.njydsz.common.web.filter.TenantMdcFilter;

import jakarta.servlet.Filter;

/**
 * 从 CoreAutoConfiguration 迁出的 Web 层自动配置。
 *
 * <p>注册 Servlet Filter 和过滤器配置属性绑定，这些不属于 L1 基础设施层。
 *
 * @author ydsz-team
 * @since 1.1.1
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "ydsz.core", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FilterIgnoreProperties.class)
public class WebCoreAutoConfiguration {

    /**
     * 注册租户 MDC 过滤器，将 tenantId/userId/traceId 写入 SLF4J MDC。
     *
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnClass(Filter.class)
    @ConditionalOnMissingBean(name = "tenantMdcFilter")
    @ConditionalOnProperty(prefix = "ydsz.core.tenant-mdc-filter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TenantMdcFilter> tenantMdcFilter(CoreProperties properties) {
        FilterRegistrationBean<TenantMdcFilter> registration =
                new FilterRegistrationBean<>(new TenantMdcFilter());
        registration.setOrder(properties.getTenantMdcFilterOrder());
        registration.setName("tenantMdcFilter");
        return registration;
    }
}
