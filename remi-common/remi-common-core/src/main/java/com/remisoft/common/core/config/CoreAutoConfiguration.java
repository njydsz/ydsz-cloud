package com.remisoft.common.core.config;

import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.remisoft.common.core.constant.PageConstants;
import com.remisoft.common.core.metrics.CoreMetrics;
import com.remisoft.common.core.response.BaseResponse;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Core 模块自动配置类。
 *
 * <p>激活 {@link CoreProperties} 配置属性绑定，
 * 使 {@code remi.core.*} 配置项在 IDE 中获得自动补全和类型校验支持。</p>
 *
 * <p>当 Spring {@link MessageSource} 可用时，自动注册 {@link SpringMessageResolver}
 * 并绑定到 {@link BaseResponse}，使响应消息支持国际化。</p>
 *
 * <p>当 Spring Boot Actuator 的 {@link HealthIndicator} 在 classpath 可用时，
 * 自动注册 {@link CoreHealthIndicator} 暴露 Core 模块运行状态。</p>
 *
 * <p><b>启用条件：</b>当 {@code remi.core.enabled=true} 时生效（默认启用）。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.core", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({CoreProperties.class, FilterIgnoreProperties.class})
public class CoreAutoConfiguration {

    /**
     * 注册 Spring 国际化消息解析器并绑定到 {@link BaseResponse}。
     *
     * <p>当 classpath 上存在 {@link MessageSource} 且容器中有对应 Bean 时生效。
     * 采用一次性设置语义，确保解析器在应用生命周期内不可变。</p>
     *
     * @param messageSource Spring 消息源
     * @return SpringMessageResolver 实例
     */
    @Bean
    @ConditionalOnClass(MessageSource.class)
    @ConditionalOnBean(MessageSource.class)
    public SpringMessageResolver springMessageResolver(MessageSource messageSource) {
        SpringMessageResolver resolver = new SpringMessageResolver(messageSource);
        BaseResponse.setResolverIfAbsent(resolver);
        return resolver;
    }

    /**
     * 将 CoreProperties 中的分页配置传播到 PageConstants 运行时覆盖值。
     */
    @Bean
    PageConstantsInitializer pageConstantsInitializer(CoreProperties properties) {
        return new PageConstantsInitializer(properties);
    }

    /**
     * 注册 Core 模块健康检查指示器。
     *
     * <p>当 classpath 上存在 Spring Boot Actuator 的 {@link HealthIndicator} 时生效，
     * 暴露国际化解析器状态、分页配置等运行时信息。</p>
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    public CoreHealthIndicator coreHealthIndicator(CoreProperties properties) {
        return new CoreHealthIndicator(properties);
    }

    /**
     * 注册 Core 模块 Micrometer 指标（当 MeterRegistry Bean 可用时）。
     *
     * <p>仅当 classpath 上存在 {@link MeterRegistry} 且容器中有对应 Bean 时生效。
     * 注册 {@link CoreMetrics} 单例，业务过滤器可通过静态方法
     * {@link CoreMetrics#incrementResponse(String)} / {@link CoreMetrics#recordHoldTime(java.time.Duration)}
     * 上报请求级指标，无 Micrometer 时为 no-op。</p>
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public CoreMetrics coreMetrics(MeterRegistry registry) {
        return new CoreMetrics(registry);
    }

    static class PageConstantsInitializer implements org.springframework.beans.factory.SmartInitializingSingleton {

        private final CoreProperties properties;

        PageConstantsInitializer(CoreProperties properties) {
            this.properties = properties;
        }

        @Override
        public void afterSingletonsInstantiated() {
            PageConstants.init(properties);
        }
    }
}
