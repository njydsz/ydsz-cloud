package com.njydsz.common.feign.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.feign.config.FeignProperties;

/**
 * Resilience4j 熔断器适配器。
 *
 * <p>封装 Resilience4j CircuitBreaker 实例，实现 {@link FeignCircuitBreakerStrategy} 接口。
 * 每个服务名称对应一个独立的 CircuitBreaker 实例。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class Resilience4jCircuitBreakerAdapter implements FeignCircuitBreakerStrategy {

    private final FeignProperties properties;
    private final CircuitBreakerStatePersistence statePersistence;
    private final FeignCircuitBreakerMetricsExporter metricsExporter;
    private final CircuitBreakerRegistry registry;

    /**
     * @param properties        Feign 配置属性
     * @param statePersistence  状态持久化（可为 null）
     * @param metricsExporter   指标导出器（可为 null）
     */
    public Resilience4jCircuitBreakerAdapter(FeignProperties properties,
                                             CircuitBreakerStatePersistence statePersistence,
                                             FeignCircuitBreakerMetricsExporter metricsExporter) {
        this.properties = properties;
        this.statePersistence = statePersistence;
        this.metricsExporter = metricsExporter;
        this.registry = CircuitBreakerRegistry.ofDefaults();
    }

    @Override
    public boolean allowRequest(String serviceName) {
        CircuitBreaker cb = getOrCreate(serviceName);
        return cb.tryAcquirePermission();
    }

    @Override
    public void recordSuccess(String serviceName, long durationMs) {
        CircuitBreaker cb = getOrCreate(serviceName);
        cb.onSuccess(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (metricsExporter != null) {
            metricsExporter.registerServiceMetrics(serviceName);
        }
    }

    @Override
    public void recordFailure(String serviceName, long durationMs, Throwable throwable) {
        CircuitBreaker cb = getOrCreate(serviceName);
        cb.onError(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS, throwable);
    }

    private CircuitBreaker getOrCreate(String serviceName) {
        if (registry.find(serviceName).isPresent()) {
            return registry.circuitBreaker(serviceName);
        }
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(java.time.Duration.ofSeconds(3))
                .build();
        return registry.circuitBreaker(serviceName, config);
    }
}
