package com.njydsz.pmis.common.jdbc.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.jdbc.interceptor.CircuitBreakerInterceptor;
import com.njydsz.pmis.common.jdbc.monitor.DatabaseCircuitBreaker;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.plugin.Interceptor;

/**
 * 数据库熔断器自动配置
 *
 * <p>当 {@code ydsz.jdbc.circuit-breaker.enabled=true} 时，创建 {@link DatabaseCircuitBreaker}
 * Bean 并注册 {@link CircuitBreakerInterceptor} 到 MyBatis 拦截器链。
 *
 * <p>{@link CircuitBreakerInterceptor} 作为 MyBatis 外层拦截器（{@link Interceptor}）注册，
 * Spring Boot 会自动将其注入到所有 {@link org.apache.ibatis.session.SqlSessionFactory} 中。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 * @see DatabaseCircuitBreaker
 * @see CircuitBreakerInterceptor
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Interceptor.class)
@ConditionalOnProperty(prefix = "ydsz.jdbc.circuit-breaker", name = "enabled", havingValue = "true")
public class DatabaseCircuitBreakerAutoConfiguration {

    /**
     * 创建数据库熔断器实例并绑定 Micrometer 指标
     *
     * @param properties            熔断器配置属性
     * @param meterRegistryProvider Micrometer 注册表 Provider
     * @return DatabaseCircuitBreaker 实例
     */
    @Bean
    public DatabaseCircuitBreaker databaseCircuitBreaker(CircuitBreakerProperties properties,
                                                         ObjectProvider<MeterRegistry> meterRegistryProvider) {
        DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(
                properties.getFailureThreshold(),
                properties.getOpenDurationMillis(),
                properties.getHalfOpenProbeSize());
        // 绑定 Micrometer 指标
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            breaker.bindTo(registry);
        }
        return breaker;
    }

    /**
     * 注册熔断器拦截器到 MyBatis
     *
     * @param circuitBreaker 数据库熔断器实例
     * @return CircuitBreakerInterceptor 实例
     */
    @Bean
    public CircuitBreakerInterceptor circuitBreakerInterceptor(DatabaseCircuitBreaker circuitBreaker,
                                                                 CircuitBreakerProperties properties) {
        log.info("数据库熔断器已启用 (failureThreshold={}, openDuration={}ms, halfOpenProbeSize={})",
                properties.getFailureThreshold(), properties.getOpenDurationMillis(), properties.getHalfOpenProbeSize());
        return new CircuitBreakerInterceptor(circuitBreaker);
    }
}
