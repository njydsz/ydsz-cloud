package com.njydsz.common.jdbc.config;

import java.sql.SQLException;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.jdbc.interceptor.CircuitBreakerInterceptor;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据库熔断器自动配置
 *
 * <p>当 {@code ydsz.jdbc.circuit-breaker.enabled=true} 时，创建基于 Resilience4j 的
 * {@link CircuitBreakerRegistry} 并注册 {@link CircuitBreakerInterceptor} 到 MyBatis 拦截器链。
 *
 * <p>配置映射：
 * <ul>
 *   <li>{@code failure-threshold} → 滑动窗口大小（slidingWindowSize），失败率达到 50% 触发熔断</li>
 *   <li>{@code open-duration-millis} → 熔断持续时间（waitDurationInOpenState）</li>
 *   <li>{@code half-open-probe-size} → 半开状态允许的探测调用数（permittedNumberOfCallsInHalfOpenState）</li>
 * </ul>
 *
 * <p>异常计数：仅 {@link SQLException} 及其包装异常计入失败，业务异常不计入，避免误熔断。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CircuitBreakerInterceptor
 * @see CircuitBreaker
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(prefix = "ydsz.jdbc.circuit-breaker", name = "enabled", havingValue = "true")
public class DatabaseCircuitBreakerAutoConfiguration {

    /**
     * 创建 Resilience4j 熔断器注册表
     *
     * @param properties            熔断器配置属性
     * @param meterRegistryProvider Micrometer 注册表 Provider
     * @return CircuitBreakerRegistry 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerProperties properties,
                                                         ObjectProvider<MeterRegistry> meterRegistryProvider) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(Math.max(properties.getFailureThreshold(), 2))
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(properties.getOpenDurationMillis()))
                .permittedNumberOfCallsInHalfOpenState(properties.getHalfOpenProbeSize())
                .recordException(DatabaseCircuitBreakerAutoConfiguration::isDatabaseException)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        }

        log.info("数据库熔断器已启用 (Resilience4j: windowSize={}, waitDuration={}ms, halfOpenProbeSize={})",
                properties.getFailureThreshold(), properties.getOpenDurationMillis(), properties.getHalfOpenProbeSize());
        return registry;
    }

    /**
     * 判断异常是否属于数据库故障。
     *
     * <p>仅 {@link SQLException} 及其包装异常计入熔断失败计数，业务异常不计入。
     *
     * @param throwable 待判断异常
     * @return 属于数据库故障时返回 true
     */
    static boolean isDatabaseException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SQLException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 注册熔断器拦截器到 MyBatis
     *
     * @param circuitBreakerRegistry Resilience4j 熔断器注册表
     * @return CircuitBreakerInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerInterceptor circuitBreakerInterceptor(CircuitBreakerRegistry circuitBreakerRegistry) {
        return new CircuitBreakerInterceptor(circuitBreakerRegistry);
    }
}
