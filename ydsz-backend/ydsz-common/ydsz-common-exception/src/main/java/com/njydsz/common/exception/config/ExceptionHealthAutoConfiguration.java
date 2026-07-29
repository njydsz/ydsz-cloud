package com.njydsz.common.exception.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.health.ExceptionHealthIndicator;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.registry.ResultCodeRegistry;

/**
 * 异常模块健康检查自动配置
 *
 * <p>当 spring-boot-health 在类路径上时，自动注册 {@link ExceptionHealthIndicator}。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see ExceptionHealthIndicator
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@EnableConfigurationProperties(ExceptionProperties.class)
public class ExceptionHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExceptionHealthIndicator.class)
    public ExceptionHealthIndicator exceptionHealthIndicator(
            ExceptionProperties properties,
            ObjectProvider<ExceptionMetrics> metricsProvider,
            ObjectProvider<ResultCodeRegistry> resultCodeRegistryProvider) {
        return new ExceptionHealthIndicator(properties, metricsProvider,
                resultCodeRegistryProvider);
    }
}
