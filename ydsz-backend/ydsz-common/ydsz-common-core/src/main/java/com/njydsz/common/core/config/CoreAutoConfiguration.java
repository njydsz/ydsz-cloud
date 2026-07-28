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
import org.springframework.core.Ordered;

import com.njydsz.common.core.context.TenantMdcFilter;
import com.njydsz.common.core.dag.SpELConditionEvaluator;
import com.njydsz.common.core.featureflag.FeatureFlagService;
import com.njydsz.common.core.featureflag.FeatureToggleAspect;
import com.njydsz.common.core.health.CoreHealthIndicator;
import com.njydsz.common.core.response.BaseResponse;

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
     * 注册 SpEL 条件评估器（DAG 条件分支节点使用）。
     *
     * @return SpELConditionEvaluator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SpELConditionEvaluator spELConditionEvaluator() {
        return new SpELConditionEvaluator();
    }

    /**
     * 注册 FeatureToggle AOP 切面。
     *
     * <p>当容器中存在 {@link FeatureFlagService} Bean 且 classpath 上有 AspectJ 时生效。
     *
     * @param featureFlagService 特性开关服务
     * @return FeatureToggleAspect 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FeatureFlagService.class)
    public FeatureToggleAspect featureToggleAspect(FeatureFlagService featureFlagService) {
        return new FeatureToggleAspect(featureFlagService);
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
    public FilterRegistrationBean<TenantMdcFilter> tenantMdcFilter() {
        FilterRegistrationBean<TenantMdcFilter> registration =
                new FilterRegistrationBean<>(new TenantMdcFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
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
    public CoreHealthIndicator coreHealthIndicator(CoreProperties properties) {
        return new CoreHealthIndicator(properties);
    }
}
