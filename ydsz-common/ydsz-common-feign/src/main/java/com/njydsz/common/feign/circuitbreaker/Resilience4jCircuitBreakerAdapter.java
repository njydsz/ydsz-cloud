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

    @Override
    public CircuitBreakerState getState(String serviceName) {
        CircuitBreaker cb = getOrCreate(serviceName);
        return switch (cb.getState()) {
            case CLOSED -> CircuitBreakerState.CLOSED;
            case OPEN -> CircuitBreakerState.OPEN;
            case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
            case FORCED_OPEN -> CircuitBreakerState.FORCED_OPEN;
            default -> CircuitBreakerState.CLOSED;
        };
    }

    @Override
    public CircuitBreakerMetrics getMetrics(String serviceName) {
        CircuitBreaker cb = getOrCreate(serviceName);
        CircuitBreaker.Metrics metrics = cb.getMetrics();
        return new CircuitBreakerMetrics() {
            @Override
            public float getFailureRate() {
                return metrics.getFailureRate();
            }

            @Override
            public int getTotalCalls() {
                return metrics.getNumberOfBufferedCalls() + metrics.getNumberOfNotPermittedCalls();
            }

            @Override
            public int getSuccessfulCalls() {
                return metrics.getNumberOfSuccessfulCalls();
            }

            @Override
            public int getFailedCalls() {
                return metrics.getNumberOfFailedCalls();
            }

            @Override
            public int getSlowCalls() {
                return metrics.getNumberOfSlowCalls() + metrics.getNumberOfSlowFailedCalls() + metrics.getNumberOfSlowSuccessfulCalls();
            }

            @Override
            public long getAverageDuration() {
                return metrics.getAverageDuration().toMillis();
            }
        };
    }

    private CircuitBreaker getOrCreate(String serviceName) {
        if (registry.find(serviceName).isPresent()) {
            return registry.circuitBreaker(serviceName);
        }
        // 熔断参数从配置读取（ydsz.feign.circuit-breaker.*），不再硬编码，支持按环境调优
        FeignProperties.CircuitBreaker cbConfig = properties.getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cbConfig.getFailureRateThreshold())
                .slowCallRateThreshold(cbConfig.getSlowCallRateThreshold())
                .slowCallDurationThreshold(java.time.Duration.ofMillis(cbConfig.getSlowCallDurationMs()))
                .waitDurationInOpenState(java.time.Duration.ofMillis(cbConfig.getWaitDurationMs()))
                .minimumNumberOfCalls(cbConfig.getMinimumNumberOfCalls())
                .slidingWindowSize(cbConfig.getSlidingWindowSize())
                .build();
        return registry.circuitBreaker(serviceName, config);
    }
}
