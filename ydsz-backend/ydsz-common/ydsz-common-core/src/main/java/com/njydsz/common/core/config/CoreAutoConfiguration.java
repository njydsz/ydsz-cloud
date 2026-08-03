package com.njydsz.common.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.context.TenantMdcFilter;
import com.njydsz.common.core.health.CoreHealthIndicator;
import com.njydsz.common.core.response.BaseResponse;

import jakarta.servlet.Filter;

/**
 * Core 模块自动配置类。
 *
 * <p>激活 {@link CoreProperties} 配置属性绑定，
 * 使 {@code ydsz.core.*} 配置项在 IDE 中获得自动补全和类型校验支持。</p>
 *
 * <p>当 Spring {@link MessageSource} 可用时，自动注册 {@link SpringMessageResolver}
 * 并绑定到 {@link BaseResponse}，使响应消息支持国际化。</p>
 *
 * <p><b>启用条件：</b>当 {@code ydsz.core.enabled=true} 时生效（默认启用）。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.core", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({CoreProperties.class, FilterIgnoreProperties.class})
public class CoreAutoConfiguration {

    /**
     * 注册 Spring 国际化消息解析器并绑定到 {@link BaseResponse}。
     *
     * <p>当 classpath 上存在 {@link MessageSource} 且容器中有对应 Bean 时生效。</p>
     *
     * @param messageSource Spring 消息源
     * @return SpringMessageResolver 实例
     */
    @Bean
    @ConditionalOnClass(MessageSource.class)
    @ConditionalOnBean(MessageSource.class)
    public SpringMessageResolver springMessageResolver(MessageSource messageSource) {
        SpringMessageResolver resolver = new SpringMessageResolver(messageSource);
        BaseResponse.setResolver(resolver);
        return resolver;
    }

    /**
     * 注册租户 MDC 过滤器，将 tenantId/userId/traceId 写入 SLF4J MDC。
     *
     * @return FilterRegistrationBean 包装的 TenantMdcFilter
     */
    @Bean
    @ConditionalOnClass(Filter.class)
    @ConditionalOnProperty(prefix = "ydsz.core.tenant-mdc-filter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TenantMdcFilter> tenantMdcFilter(CoreProperties properties) {
        FilterRegistrationBean<TenantMdcFilter> registration =
                new FilterRegistrationBean<>(new TenantMdcFilter());
        registration.setOrder(properties.getTenantMdcFilterOrder());
        registration.setName("tenantMdcFilter");
        return registration;
    }

    /**
     * 注册 Core 模块健康指标（当 Actuator 在 classpath 时生效）。
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnMissingBean(name = "coreHealthIndicator")
    public CoreHealthIndicator coreHealthIndicator() {
        return new CoreHealthIndicator();
    }

    /**
     * 将 CoreProperties 中的分页配置传播到 PageConstants 运行时覆盖值。
     */
    @Bean
    PageConstantsInitializer pageConstantsInitializer(CoreProperties properties) {
        return new PageConstantsInitializer(properties);
    }

    static class PageConstantsInitializer implements org.springframework.beans.factory.SmartInitializingSingleton {

        private final CoreProperties properties;

        PageConstantsInitializer(CoreProperties properties) {
            this.properties = properties;
        }

        @Override
        public void afterSingletonsInstantiated() {
            PageConstants.setMaxPageSize(properties.getMaxPageSize());
            PageConstants.setDefaultPageSize(properties.getDefaultPageSize());
        }
    }
}
