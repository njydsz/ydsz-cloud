package com.njydsz.pmis.common.feign.circuitbreaker;

import com.njydsz.pmis.common.feign.config.FeignProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Resilience4j 的 Feign 熔断器适配器。
 *
 * <p>将 Resilience4j 的 {@link io.github.resilience4j.circuitbreaker.CircuitBreaker}
 * 适配到 {@link FeignCircuitBreakerStrategy} 接口，提供：
 * <ul>
 *   <li>按服务维度隔离的熔断器实例</li>
 *   <li>可配置的失败率、慢调用率阈值</li>
 *   <li>滑动窗口统计（基于次数或时间）</li>
 *   <li>自动状态转换：CLOSED → OPEN → HALF_OPEN → CLOSED</li>
 * </ul>
 *
 * <p><b>熔断器状态转换：</b>
 * <pre>
 * CLOSED（正常） → 失败率/慢调用率超过阈值 → OPEN（熔断）
 * OPEN（熔断） → 等待时间到期 → HALF_OPEN（半开）
 * HALF_OPEN（半开） → 成功 → CLOSED（恢复）
 * HALF_OPEN（半开） → 失败 → OPEN（重新熔断）
 * </pre>
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>
 * ydsz:
 *   feign:
 *     circuit-breaker:
 *       enabled: true
 *       failure-rate-threshold: 50
 *       slow-call-rate-threshold: 100
 *       slow-call-duration-threshold: 3000
 *       permitted-number-of-calls-in-half-open-state: 10
 *       sliding-window-size: 100
 *       sliding-window-type: COUNT_BASED
 *       wait-duration-in-open-state: 60
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see FeignCircuitBreaker
 * @see FeignCircuitBreakerStrategy
 */
public class Resilience4jCircuitBreakerAdapter implements FeignCircuitBreakerStrategy {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jCircuitBreakerAdapter.class);

    /** Resilience4j 资源名前缀 */
    private static final String RESOURCE_PREFIX = "feign:";

    private final ConcurrentHashMap<String, io.github.resilience4j.circuitbreaker.CircuitBreaker> circuitBreakers =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, CircuitBreakerState> stateCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, CircuitBreakerMetrics> metricsCache = new ConcurrentHashMap<>();

    private final CircuitBreakerConfig config;

    /**
     * 根据 FeignProperties 配置创建适配器。
     *
     * @param properties Feign 配置属性
     */
    public Resilience4jCircuitBreakerAdapter(FeignProperties properties) {
        FeignProperties.CircuitBreaker cb = properties.getCircuitBreaker();
        this.config = buildConfig(cb);
        log.info("[Resilience4jCircuitBreaker] 初始化完成, failureRateThreshold={}%, " +
                        "slowCallRateThreshold={}%, slowCallDurationThreshold={}ms, " +
                        "permittedCallsInHalfOpen={}, slidingWindowSize={}, slidingWindowType={}, " +
                        "waitDurationInOpenState={}s",
                cb.getFailureRateThreshold(), cb.getSlowCallRateThreshold(),
                cb.getSlowCallDurationThreshold(), cb.getPermittedNumberOfCallsInHalfOpenState(),
                cb.getSlidingWindowSize(), cb.getSlidingWindowType(), cb.getWaitDurationInOpenState());
    }

    /**
     * 根据自定义配置创建适配器。
     *
     * @param config Resilience4j CircuitBreakerConfig
     */
    public Resilience4jCircuitBreakerAdapter(CircuitBreakerConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "resilience4j";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean allowRequest(String serviceName) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        CircuitBreakerState state = toState(cb.getState());
        stateCache.put(serviceName, state);
        return cb.tryAcquirePermission();
    }

    @Override
    public void recordSuccess(String serviceName, long elapsedTime) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        cb.onSuccess(elapsedTime, TimeUnit.MILLISECONDS);
        stateCache.put(serviceName, toState(cb.getState()));
        updateMetrics(serviceName, true, elapsedTime);
    }

    @Override
    public void recordFailure(String serviceName, long elapsedTime, Throwable throwable) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        cb.onError(elapsedTime, TimeUnit.MILLISECONDS, throwable);
        stateCache.put(serviceName, toState(cb.getState()));
        log.warn("[Resilience4jCircuitBreaker] 调用失败, service={}, cause={}", serviceName, throwable.getMessage());
        updateMetrics(serviceName, false, elapsedTime);
    }

    @Override
    public CircuitBreakerState getState(String serviceName) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb == null) {
            return CircuitBreakerState.CLOSED;
        }
        return toState(cb.getState());
    }

    @Override
    public CircuitBreakerMetrics getMetrics(String serviceName) {
        return metricsCache.getOrDefault(serviceName, new CircuitBreakerMetrics());
    }

    @Override
    public void reset(String serviceName) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb != null) {
            cb.reset();
        }
        stateCache.put(serviceName, CircuitBreakerState.CLOSED);
        metricsCache.remove(serviceName);
        log.info("[Resilience4jCircuitBreaker] 熔断器已重置, service={}", serviceName);
    }

    /**
     * 执行带熔断保护的调用（使用 Resilience4j 装饰器）。
     *
     * @param serviceName 服务名称
     * @param supplier    调用逻辑
     * @param <T>         返回值类型
     * @return 调用结果
     */
    public <T> T execute(String serviceName, Supplier<T> supplier) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        return io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateSupplier(cb, supplier).get();
    }

    /**
     * 执行带熔断保护和降级的调用。
     *
     * @param serviceName 服务名称
     * @param supplier    调用逻辑
     * @param fallback    降级逻辑
     * @param <T>         返回值类型
     * @return 调用结果或降级结果
     */
    public <T> T executeWithFallback(String serviceName, Supplier<T> supplier, Supplier<T> fallback) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        try {
            return io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateSupplier(cb, supplier).get();
        } catch (Exception e) {
            log.warn("[Resilience4jCircuitBreaker] 调用失败, service={}, 使用降级逻辑, cause={}", serviceName, e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 获取或创建指定服务的 Resilience4j CircuitBreaker 实例。
     */
    private io.github.resilience4j.circuitbreaker.CircuitBreaker getOrCreateCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, name ->
                io.github.resilience4j.circuitbreaker.CircuitBreaker.of(toResource(name), config));
    }

    /**
     * 将服务名转换为 Resilience4j 资源名。
     */
    private String toResource(String serviceName) {
        return RESOURCE_PREFIX + serviceName;
    }

    /**
     * 构建 Resilience4j CircuitBreakerConfig。
     */
    private CircuitBreakerConfig buildConfig(FeignProperties.CircuitBreaker cb) {
        SlidingWindowType windowType = "TIME_BASED".equalsIgnoreCase(cb.getSlidingWindowType())
                ? SlidingWindowType.TIME_BASED
                : SlidingWindowType.COUNT_BASED;

        return CircuitBreakerConfig.custom()
                .failureRateThreshold(cb.getFailureRateThreshold())
                .slowCallRateThreshold(cb.getSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(cb.getSlowCallDurationThreshold()))
                .permittedNumberOfCallsInHalfOpenState(cb.getPermittedNumberOfCallsInHalfOpenState())
                .slidingWindowSize(cb.getSlidingWindowSize())
                .slidingWindowType(windowType)
                .waitDurationInOpenState(Duration.ofSeconds(cb.getWaitDurationInOpenState()))
                .build();
    }

    /**
     * 将 Resilience4j CircuitBreaker.State 转换为内部状态枚举。
     */
    private CircuitBreakerState toState(io.github.resilience4j.circuitbreaker.CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> CircuitBreakerState.CLOSED;
            case OPEN -> CircuitBreakerState.OPEN;
            case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
            case DISABLED -> CircuitBreakerState.DISABLED;
            case FORCED_OPEN -> CircuitBreakerState.FORCED_OPEN;
            case METRICS_ONLY -> CircuitBreakerState.CLOSED;
        };
    }

    /**
     * 更新本地指标。
     *
     * <p>使用 {@link java.util.concurrent.atomic.LongAdder#increment()} 原子递增，
     * 替代原先非原子的 read-modify-write（getTotalCalls()+1 → setTotalCalls），
     * 消除高并发下的竞态条件。
     */
    private void updateMetrics(String serviceName, boolean success, long elapsedTime) {
        CircuitBreakerMetrics m = metricsCache.computeIfAbsent(serviceName, k -> new CircuitBreakerMetrics());
        m.incrementTotalCalls();
        if (success) {
            m.incrementSuccessfulCalls();
        } else {
            m.incrementFailedCalls();
        }
        if (elapsedTime >= config.getSlowCallDurationThreshold().toMillis()) {
            m.incrementSlowCalls();
        }
        // 速率计算基于最新计数值，容忍短暂的不一致
        long total = m.getTotalCalls();
        if (total > 0) {
            m.setFailureRate((double) m.getFailedCalls() / total * 100.0);
            m.setSlowCallRate((double) m.getSlowCalls() / total * 100.0);
        }
    }
}
