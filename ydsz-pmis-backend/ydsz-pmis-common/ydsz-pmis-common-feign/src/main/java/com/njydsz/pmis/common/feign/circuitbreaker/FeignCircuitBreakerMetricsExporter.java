package com.njydsz.pmis.common.feign.circuitbreaker;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器指标导出到 Spring Boot Actuator Metrics
 *
 * <p>自动注册熔断状态、失败率、调用次数等关键指标到 Micrometer，
 * 可通过 Actuator /actuator/metrics 端点查看。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code feign.circuit.breaker.state} - 熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN）</li>
 *   <li>{@code feign.circuit.breaker.failure.rate} - 失败率（百分比）</li>
 *   <li>{@code feign.circuit.breaker.total.calls} - 总调用次数</li>
 *   <li>{@code feign.circuit.breaker.success.calls} - 成功调用次数</li>
 *   <li>{@code feign.circuit.breaker.failed.calls} - 失败调用次数</li>
 *   <li>{@code feign.circuit.breaker.slow.calls} - 慢调用次数</li>
 *   <li>{@code feign.circuit.breaker.avg.duration} - 平均耗时（毫秒）</li>
 * </ul>
 *
 * <p>指标标签：
 * <ul>
 *   <li>{@code service} - 服务名称</li>
 * </ul>
 *
 * <p><b>使用方式：</b></p>
 * 当 {@link FeignCircuitBreakerStrategy} 和 {@link MeterRegistry} 同时存在时自动启用。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@ConditionalOnClass({MeterRegistry.class, FeignCircuitBreakerStrategy.class})
@ConditionalOnBean({FeignCircuitBreakerStrategy.class, MeterRegistry.class})
public class FeignCircuitBreakerMetricsExporter {

    private static final Logger log = LoggerFactory.getLogger(FeignCircuitBreakerMetricsExporter.class);

    /** 指标名称前缀 */
    private static final String PREFIX = "feign.circuit.breaker";
    /** 服务名称标签键 */
    private static final String TAG_SERVICE = "service";

    /** Feign 熔断器策略 */
    private final FeignCircuitBreakerStrategy circuitBreakerStrategy;
    /** Micrometer 指标注册表 */
    private final MeterRegistry meterRegistry;

    /**
     * 已注册的指标服务集合，避免重复注册
     */
    private final Set<String> registeredServices = ConcurrentHashMap.newKeySet();

    /**
     * 构造熔断器指标导出器。
     *
     * @param circuitBreakerStrategy Feign 熔断器策略
     * @param meterRegistry  Micrometer 指标注册表
     */
    public FeignCircuitBreakerMetricsExporter(FeignCircuitBreakerStrategy circuitBreakerStrategy, MeterRegistry meterRegistry) {
        this.circuitBreakerStrategy = circuitBreakerStrategy;
        this.meterRegistry = meterRegistry;
        log.info("[FeignCircuitBreakerMetricsExporter] 熔断器指标导出已启用");
    }

    /**
     * 注册指定服务的熔断器指标
     *
     * @param serviceName 服务名称
     */
    public void registerServiceMetrics(String serviceName) {
        if (registeredServices.contains(serviceName)) {
            return;
        }
        registeredServices.add(serviceName);

        // 熔断器状态指标: 0=CLOSED, 1=OPEN, 2=HALF_OPEN, 3=FORCED_OPEN
        Gauge.builder(PREFIX + ".state", () -> {
                    FeignCircuitBreakerStrategy.CircuitBreakerState state = circuitBreakerStrategy.getState(serviceName);
                    return state == FeignCircuitBreakerStrategy.CircuitBreakerState.CLOSED ? 0
                            : state == FeignCircuitBreakerStrategy.CircuitBreakerState.OPEN ? 1
                            : state == FeignCircuitBreakerStrategy.CircuitBreakerState.HALF_OPEN ? 2
                            : state == FeignCircuitBreakerStrategy.CircuitBreakerState.FORCED_OPEN ? 3 : -1;
                })
                .tag(TAG_SERVICE, serviceName)
                .description("Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN, 3=FORCED_OPEN")
                .register(meterRegistry);

        // 失败率
        Gauge.builder(PREFIX + ".failure.rate", () -> {
                    FeignCircuitBreakerStrategy.CircuitBreakerMetrics metrics = circuitBreakerStrategy.getMetrics(serviceName);
                    return metrics.getFailureRate();
                })
                .tag(TAG_SERVICE, serviceName)
                .description("Failure rate percentage")
                .register(meterRegistry);

        // 总调用次数
        Gauge.builder(PREFIX + ".total.calls", () -> {
                    FeignCircuitBreakerStrategy.CircuitBreakerMetrics metrics = circuitBreakerStrategy.getMetrics(serviceName);
                    return (double) metrics.getTotalCalls();
                })
                .tag(TAG_SERVICE, serviceName)
                .description("Total call count")
                .register(meterRegistry);

        // 成功调用次数
        Gauge.builder(PREFIX + ".success.calls", () -> {
                    FeignCircuitBreakerStrategy.CircuitBreakerMetrics metrics = circuitBreakerStrategy.getMetrics(serviceName);
                    return (double) metrics.getSuccessfulCalls();
                })
                .tag(TAG_SERVICE, serviceName)
                .description("Successful call count")
                .register(meterRegistry);

        // 失败调用次数
        Gauge.builder(PREFIX + ".failed.calls", () -> {
                    FeignCircuitBreakerStrategy.CircuitBreakerMetrics metrics = circuitBreakerStrategy.getMetrics(serviceName);
                    return (double) metrics.getFailedCalls();
                })
                .tag(TAG_SERVICE, serviceName)
                .description("Failed call count")
                .register(meterRegistry);

        // 慢调用次数
        Gauge.builder(PREFIX + ".slow.calls", () -> {
                    FeignCircuitBreakerStrategy.CircuitBreakerMetrics metrics = circuitBreakerStrategy.getMetrics(serviceName);
                    return (double) metrics.getSlowCalls();
                })
                .tag(TAG_SERVICE, serviceName)
                .description("Slow call count")
                .register(meterRegistry);

        // 平均耗时
        Gauge.builder(PREFIX + ".avg.duration", () -> {
                    FeignCircuitBreakerStrategy.CircuitBreakerMetrics metrics = circuitBreakerStrategy.getMetrics(serviceName);
                    return (double) metrics.getAverageDuration();
                })
                .tag(TAG_SERVICE, serviceName)
                .description("Average call duration in milliseconds")
                .register(meterRegistry);

        log.debug("[FeignCircuitBreakerMetricsExporter] 已注册服务指标: {}", serviceName);
    }

    /**
     * 注销指定服务的熔断器指标
     *
     * @param serviceName 服务名称
     */
    public void unregisterServiceMetrics(String serviceName) {
        registeredServices.remove(serviceName);
        log.debug("[FeignCircuitBreakerMetricsExporter] 已注销服务指标: {}", serviceName);
    }
}

