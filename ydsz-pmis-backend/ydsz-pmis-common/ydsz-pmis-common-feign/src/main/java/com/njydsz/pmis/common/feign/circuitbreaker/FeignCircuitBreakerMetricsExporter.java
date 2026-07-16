package com.njydsz.pmis.common.feign.circuitbreaker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 熔断器指标导出到 Spring Boot Actuator Metrics。
 *
 * <p>自动注册熔断状态、失败率、调用次数等关键指标到 Micrometer，
 * 可通过 Actuator /actuator/metrics 端点查看。
 *
 * <p><b>自动注册机制：</b>当 {@link Resilience4jCircuitBreakerAdapter} 创建新熔断器时，
 * 通过回调自动注册该服务的指标，无需外部手动调用。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code feign.circuit.breaker.state} - 熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN, 3=FORCED_OPEN）</li>
 *   <li>{@code feign.circuit.breaker.failure.rate} - 失败率（百分比）</li>
 *   <li>{@code feign.circuit.breaker.total.calls} - 总调用次数</li>
 *   <li>{@code feign.circuit.breaker.success.calls} - 成功调用次数</li>
 *   <li>{@code feign.circuit.breaker.failed.calls} - 失败调用次数</li>
 *   <li>{@code feign.circuit.breaker.slow.calls} - 慢调用次数</li>
 *   <li>{@code feign.circuit.breaker.avg.duration} - 平均耗时（毫秒）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ConditionalOnClass({MeterRegistry.class, FeignCircuitBreakerStrategy.class})
@ConditionalOnBean({FeignCircuitBreakerStrategy.class, MeterRegistry.class})
public class FeignCircuitBreakerMetricsExporter {

    private static final Logger log = LoggerFactory.getLogger(FeignCircuitBreakerMetricsExporter.class);

    private static final String PREFIX = "feign.circuit.breaker";
    private static final String TAG_SERVICE = "service";

    private final FeignCircuitBreakerStrategy circuitBreakerStrategy;
    private final MeterRegistry meterRegistry;
    private final Set<String> registeredServices = ConcurrentHashMap.newKeySet();

    /**
     * 构造熔断器指标导出器。
     *
     * @param circuitBreakerStrategy Feign 熔断器策略
     * @param meterRegistry          Micrometer 指标注册表
     */
    public FeignCircuitBreakerMetricsExporter(FeignCircuitBreakerStrategy circuitBreakerStrategy,
                                               MeterRegistry meterRegistry) {
        this.circuitBreakerStrategy = circuitBreakerStrategy;
        this.meterRegistry = meterRegistry;
        log.info("[FeignCircuitBreakerMetricsExporter] 熔断器指标导出已启用");
    }

    /**
     * 自动注册指定服务的熔断器指标。
     *
     * <p>当熔断器策略首次接触某服务时调用此方法，自动注册所有 Gauge 指标。
     * 使用 {@link ConcurrentHashMap#newKeySet()} 确保每个服务只注册一次。
     *
     * @param serviceName 服务名称
     */
    public void registerServiceMetrics(String serviceName) {
        if (registeredServices.contains(serviceName)) {
            return;
        }
        registeredServices.add(serviceName);

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

        Gauge.builder(PREFIX + ".failure.rate", () ->
                        circuitBreakerStrategy.getMetrics(serviceName).getFailureRate())
                .tag(TAG_SERVICE, serviceName)
                .description("Failure rate percentage")
                .register(meterRegistry);

        Gauge.builder(PREFIX + ".total.calls", () ->
                        (double) circuitBreakerStrategy.getMetrics(serviceName).getTotalCalls())
                .tag(TAG_SERVICE, serviceName)
                .description("Total call count")
                .register(meterRegistry);

        Gauge.builder(PREFIX + ".success.calls", () ->
                        (double) circuitBreakerStrategy.getMetrics(serviceName).getSuccessfulCalls())
                .tag(TAG_SERVICE, serviceName)
                .description("Successful call count")
                .register(meterRegistry);

        Gauge.builder(PREFIX + ".failed.calls", () ->
                        (double) circuitBreakerStrategy.getMetrics(serviceName).getFailedCalls())
                .tag(TAG_SERVICE, serviceName)
                .description("Failed call count")
                .register(meterRegistry);

        Gauge.builder(PREFIX + ".slow.calls", () ->
                        (double) circuitBreakerStrategy.getMetrics(serviceName).getSlowCalls())
                .tag(TAG_SERVICE, serviceName)
                .description("Slow call count")
                .register(meterRegistry);

        Gauge.builder(PREFIX + ".avg.duration", () ->
                        (double) circuitBreakerStrategy.getMetrics(serviceName).getAverageDuration())
                .tag(TAG_SERVICE, serviceName)
                .description("Average call duration in milliseconds")
                .register(meterRegistry);

        log.debug("[FeignCircuitBreakerMetricsExporter] 已自动注册服务指标: {}", serviceName);
    }

    /**
     * 注销指定服务的熔断器指标。
     *
     * @param serviceName 服务名称
     */
    public void unregisterServiceMetrics(String serviceName) {
        registeredServices.remove(serviceName);
        log.debug("[FeignCircuitBreakerMetricsExporter] 已注销服务指标: {}", serviceName);
    }

    /**
     * 获取已注册的服务集合。
     *
     * @return 已注册服务名称集合的不可变副本
     */
    public Set<String> getRegisteredServices() {
        return Set.copyOf(registeredServices);
    }
}
