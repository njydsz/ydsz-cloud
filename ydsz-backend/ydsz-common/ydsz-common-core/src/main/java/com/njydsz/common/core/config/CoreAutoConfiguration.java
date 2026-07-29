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
import com.njydsz.common.core.metrics.CoreMetrics;
import com.njydsz.common.core.metrics.CoreMetricsCallback;
import com.njydsz.common.core.response.BaseResponse;
import org.springframework.beans.factory.ObjectProvider;

import jakarta.servlet.Filter;

/**
 * Core 模块自动配置类
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
     * <p>当 classpath 上存在 {@link MessageSource} 且容器中有对应 Bean 时生效。
     * 将 Spring 的 MessageSource 适配为 {@link BaseResponse.MessageResolver}，
     * 使 {@code BaseResponse.success()} 和 {@code BaseResponse.error()} 中的消息
     * 支持 i18n 国际化解析。</p>
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
     * <p>在 Web 场景下自动注册，优先级高于业务过滤器。
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
     *
     * @param properties 核心配置属性
     * @return CoreHealthIndicator 实例
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnMissingBean(name = "coreHealthIndicator")
    public CoreHealthIndicator coreHealthIndicator(CoreProperties properties,
                                                    FilterIgnoreProperties filterIgnoreProperties) {
        return new CoreHealthIndicator(properties, filterIgnoreProperties);
    }

    /**
     * 自动注册指标回调（当 classpath 上有 {@link CoreMetricsCallback} 实现时生效）。
     *
     * <p>上层模块（如 {@code ydsz-common-base}）可提供 {@link CoreMetricsCallback} Bean，
     * 将 core 模块的关键操作指标桥接到 Micrometer / Prometheus 等监控系统。
     * 未提供时使用 NOOP 空操作，零性能开销。</p>
     *
     * @param callbackProvider 指标回调 ObjectProvider
     * @return 用于生命周期管理的 InitializingBean
     */
    @Bean
    org.springframework.beans.factory.InitializingBean coreMetricsRegistrar(
            ObjectProvider<CoreMetricsCallback> callbackProvider) {
        return () -> {
            CoreMetricsCallback callback = callbackProvider.getIfAvailable();
            if (callback != null) {
                CoreMetrics.setCallback(callback);
            }
        };
    }

    /**
     * 将 CoreProperties 中的分页配置传播到 PageConstants 运行时覆盖值。
     *
     * <p>使 {@link PageConstants#getMaxPageSize()} / {@link PageConstants#getDefaultPageSize()}
     * 在运行时反映用户配置，消除编译期常量与运行时配置的脱节。
     *
     * @param properties 核心配置属性
     */
    @Bean
    PageConstantsInitializer pageConstantsInitializer(CoreProperties properties) {
        return new PageConstantsInitializer(properties);
    }

    /**
     * 在容器启动时将 CoreProperties 的分页配置同步到 PageConstants。
     *
     * <p>实现 {@link org.springframework.beans.factory.SmartInitializingSingleton}
     * 确保在所有 Bean 初始化完成后执行，避免依赖顺序问题。
     */
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
