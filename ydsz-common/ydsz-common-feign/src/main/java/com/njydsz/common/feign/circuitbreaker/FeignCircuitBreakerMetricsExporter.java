package com.njydsz.common.feign.circuitbreaker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 熔断器指标导出到 Spring Boot Actuator Metrics。
 *
 * <p>自动注册熔断状态、失败率、调用次数等关键指标到 Micrometer， 可通过 Actuator /actuator/metrics 端点查看。
 *
 * <p><b>自动注册机制：</b>当 {@link SafeCircuitBreakerAdapter} 创建新熔断器时， 通过回调自动注册该服务的指标，无需外部手动调用。
 *
 * <p>注册的指标：
 *
 * <ul>
 *   <li>{@code feign.circuit.breaker.state} - 熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN, 3=FORCED_OPEN）
 *   <li>{@code feign.circuit.breaker.failure.rate} - 失败率（百分比）
 *   <li>{@code feign.circuit.breaker.total.calls} - 总调用次数
 *   <li>{@code feign.circuit.breaker.success.calls} - 成功调用次数
 *   <li>{@code feign.circuit.breaker.failed.calls} - 失败调用次数
 *   <li>{@code feign.circuit.breaker.slow.calls} - 慢调用次数
 *   <li>{@code feign.circuit.breaker.avg.duration} - 平均耗时（毫秒）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class FeignCircuitBreakerMetricsExporter {

  private static final Logger LOG =
      LoggerFactory.getLogger(FeignCircuitBreakerMetricsExporter.class);

  private static final String PREFIX = "feign.circuit.breaker";
  private static final String TAG_SERVICE = "service";

  private final MeterRegistry meterRegistry;
  private final Set<String> registeredServices = ConcurrentHashMap.newKeySet();

  private volatile FeignCircuitBreakerStrategy circuitBreakerStrategy;

  /**
   * 构造熔断器指标导出器。
   *
   * @param meterRegistry Micrometer 指标注册表
   */
  public FeignCircuitBreakerMetricsExporter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    LOG.info("[FeignCircuitBreakerMetricsExporter] 熔断器指标导出已启用");
  }

  /**
   * 设置熔断器策略（用于解决与策略之间的循环依赖）。
   *
   * @param circuitBreakerStrategy Feign 熔断器策略
   */
  public void setCircuitBreakerStrategy(FeignCircuitBreakerStrategy circuitBreakerStrategy) {
    this.circuitBreakerStrategy = circuitBreakerStrategy;
  }

  /**
   * 自动注册指定服务的熔断器指标。
   *
   * <p>当熔断器策略首次接触某服务时调用此方法，自动注册所有 Gauge 指标。 使用 {@link ConcurrentHashMap#newKeySet()} 确保每个服务只注册一次。
   *
   * @param serviceName 服务名称
   */
  public void registerServiceMetrics(String serviceName) {
    if (registeredServices.contains(serviceName)) {
      return;
    }
    registeredServices.add(serviceName);

    Gauge.builder(
            PREFIX + ".state",
            () -> {
              FeignCircuitBreakerStrategy strategy = circuitBreakerStrategy;
              if (strategy == null) {
                return -1.0;
              }
              FeignCircuitBreakerStrategy.CircuitBreakerState state =
                  strategy.getState(serviceName);
              return state == FeignCircuitBreakerStrategy.CircuitBreakerState.CLOSED
                  ? 0.0
                  : state == FeignCircuitBreakerStrategy.CircuitBreakerState.OPEN
                      ? 1.0
                      : state == FeignCircuitBreakerStrategy.CircuitBreakerState.HALF_OPEN
                          ? 2.0
                          : state == FeignCircuitBreakerStrategy.CircuitBreakerState.FORCED_OPEN
                              ? 3.0
                              : -1.0;
            })
        .tag(TAG_SERVICE, serviceName)
        .description("Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN, 3=FORCED_OPEN")
        .register(meterRegistry);

    Gauge.builder(
            PREFIX + ".failure.rate",
            () -> {
              FeignCircuitBreakerStrategy strategy = circuitBreakerStrategy;
              return strategy != null ? strategy.getMetrics(serviceName).getFailureRate() : 0.0;
            })
        .tag(TAG_SERVICE, serviceName)
        .description("Failure rate percentage")
        .register(meterRegistry);

    Gauge.builder(
            PREFIX + ".total.calls",
            () -> {
              FeignCircuitBreakerStrategy strategy = circuitBreakerStrategy;
              return strategy != null
                  ? (double) strategy.getMetrics(serviceName).getTotalCalls()
                  : 0.0;
            })
        .tag(TAG_SERVICE, serviceName)
        .description("Total call count")
        .register(meterRegistry);

    Gauge.builder(
            PREFIX + ".success.calls",
            () -> {
              FeignCircuitBreakerStrategy strategy = circuitBreakerStrategy;
              return strategy != null
                  ? (double) strategy.getMetrics(serviceName).getSuccessfulCalls()
                  : 0.0;
            })
        .tag(TAG_SERVICE, serviceName)
        .description("Successful call count")
        .register(meterRegistry);

    Gauge.builder(
            PREFIX + ".failed.calls",
            () -> {
              FeignCircuitBreakerStrategy strategy = circuitBreakerStrategy;
              return strategy != null
                  ? (double) strategy.getMetrics(serviceName).getFailedCalls()
                  : 0.0;
            })
        .tag(TAG_SERVICE, serviceName)
        .description("Failed call count")
        .register(meterRegistry);

    Gauge.builder(
            PREFIX + ".slow.calls",
            () -> {
              FeignCircuitBreakerStrategy strategy = circuitBreakerStrategy;
              return strategy != null
                  ? (double) strategy.getMetrics(serviceName).getSlowCalls()
                  : 0.0;
            })
        .tag(TAG_SERVICE, serviceName)
        .description("Slow call count")
        .register(meterRegistry);

    Gauge.builder(
            PREFIX + ".avg.duration",
            () -> {
              FeignCircuitBreakerStrategy strategy = circuitBreakerStrategy;
              return strategy != null
                  ? (double) strategy.getMetrics(serviceName).getAverageDuration()
                  : 0.0;
            })
        .tag(TAG_SERVICE, serviceName)
        .description("Average call duration in milliseconds")
        .register(meterRegistry);

    LOG.debug("[FeignCircuitBreakerMetricsExporter] 已自动注册服务指标: {}", serviceName);
  }

  /**
   * 注销指定服务的熔断器指标。
   *
   * @param serviceName 服务名称
   */
  public void unregisterServiceMetrics(String serviceName) {
    registeredServices.remove(serviceName);
    LOG.debug("[FeignCircuitBreakerMetricsExporter] 已注销服务指标: {}", serviceName);
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
