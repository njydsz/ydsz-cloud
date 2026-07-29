package com.njydsz.common.exception.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.alert.ExceptionAlertPublisher;
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

    /**
     * 注册异常模块健康检查指标
     *
     * @param properties             异常模块配置属性
     * @param metricsProvider        异常指标统计器（可选）
     * @param alertPublisherProvider  异常告警发布器（可选）
     * @param resultCodeRegistryProvider 错误码注册中心（可选）
     * @return ExceptionHealthIndicator 实例
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionHealthIndicator.class)
    public ExceptionHealthIndicator exceptionHealthIndicator(
            ExceptionProperties properties,
            ObjectProvider<ExceptionMetrics> metricsProvider,
            ObjectProvider<ExceptionAlertPublisher> alertPublisherProvider,
            ObjectProvider<ResultCodeRegistry> resultCodeRegistryProvider) {
        return new ExceptionHealthIndicator(properties, metricsProvider,
                alertPublisherProvider, resultCodeRegistryProvider);
    }
}
