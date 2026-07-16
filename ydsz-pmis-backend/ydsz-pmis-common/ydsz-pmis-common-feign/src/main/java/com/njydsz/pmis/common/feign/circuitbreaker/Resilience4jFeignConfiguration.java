package com.njydsz.pmis.common.feign.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.feign.config.FeignConfiguration;
import com.njydsz.pmis.common.feign.config.FeignProperties;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Resilience4j 熔断器自动配置类。
 *
 * <p>当 classpath 中存在 Resilience4j 且配置启用时，自动注册 Resilience4j 熔断器策略。
 *
 * <p><b>生效条件：</b>
 * <ul>
 *   <li>classpath 中存在 {@code CircuitBreaker}</li>
 *   <li>{@code ydsz.feign.circuit-breaker.enabled=true}</li>
 *   <li>尚未注册其他 {@link FeignCircuitBreakerStrategy} Bean</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration(after = FeignConfiguration.class)
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(prefix = "ydsz.feign.circuit-breaker", name = "enabled", havingValue = "true")
public class Resilience4jFeignConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jFeignConfiguration.class);

    /**
     * 注册 Resilience4j 熔断器策略 Bean。
     *
     * <p>自动注入 {@link CircuitBreakerStatePersistence}（用于状态持久化）
     * 和 {@link FeignCircuitBreakerMetricsExporter}（用于指标自动注册）。
     *
     * @param properties              Feign 配置属性
     * @param statePersistenceProvider 熔断状态持久化提供者（可选）
     * @param metricsExporterProvider  熔断指标导出器提供者（可选）
     * @return Resilience4jCircuitBreakerAdapter 实例
     */
    @Bean
    @ConditionalOnMissingBean(FeignCircuitBreakerStrategy.class)
    public FeignCircuitBreakerStrategy resilience4jCircuitBreakerStrategy(
            FeignProperties properties,
            ObjectProvider<CircuitBreakerStatePersistence> statePersistenceProvider,
            ObjectProvider<FeignCircuitBreakerMetricsExporter> metricsExporterProvider) {
        log.info("[Feign] 使用 Resilience4j 熔断器策略");
        return new Resilience4jCircuitBreakerAdapter(
                properties,
                statePersistenceProvider.getIfAvailable(),
                metricsExporterProvider.getIfAvailable()
        );
    }

    /**
     * 注册熔断器指标导出器。
     *
     * <p>当 Micrometer MeterRegistry 在 classpath 中时自动创建。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @return FeignCircuitBreakerMetricsExporter 实例
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public FeignCircuitBreakerMetricsExporter feignCircuitBreakerMetricsExporter(
            ObjectProvider<FeignCircuitBreakerStrategy> strategyProvider,
            MeterRegistry meterRegistry) {
        return new FeignCircuitBreakerMetricsExporter(strategyProvider.getIfAvailable(), meterRegistry);
    }
}
