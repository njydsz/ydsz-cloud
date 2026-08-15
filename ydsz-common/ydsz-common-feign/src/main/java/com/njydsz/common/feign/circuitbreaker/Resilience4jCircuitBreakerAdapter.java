package com.njydsz.common.feign.circuitbreaker;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.feign.config.FeignProperties;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;

/**
 * 基于 Resilience4j 的 Feign 熔断器适配器。
 *
 * <p>将 Resilience4j 的 {@link CircuitBreaker}
 * 适配到 {@link FeignCircuitBreakerStrategy} 接口，提供：
 * <ul>
 *   <li>按服务维度隔离的熔断器实例</li>
 *   <li>可配置的失败率、慢调用率阈值</li>
 *   <li>滑动窗口统计（基于次数或时间）</li>
 *   <li>自动状态转换：CLOSED → OPEN → HALF_OPEN → CLOSED</li>
 *   <li>熔断状态持久化到 Redis（应用重启后恢复）</li>
 *   <li>指标自动注册到 Micrometer</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class Resilience4jCircuitBreakerAdapter implements FeignCircuitBreakerStrategy {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jCircuitBreakerAdapter.class);

    private static final String RESOURCE_PREFIX = "feign:";

    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CircuitBreakerState> stateCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CircuitBreakerMetrics> metricsCache = new ConcurrentHashMap<>();

    private final CircuitBreakerConfig config;
    private final CircuitBreakerStatePersistence statePersistence;
    private final FeignCircuitBreakerMetricsExporter metricsExporter;

    /**
     * 根据 FeignProperties 配置创建适配器。
     *
     * @param properties Feign 配置属性
     */
    public Resilience4jCircuitBreakerAdapter(FeignProperties properties) {
        this(properties, null, null);
    }

    /**
     * 使用完整依赖创建适配器。
     *
     * @param properties       Feign 配置属性
     * @param statePersistence 熔断状态持久化组件（可选）
     * @param metricsExporter  熔断指标导出器（可选）
     */
    public Resilience4jCircuitBreakerAdapter(FeignProperties properties,
                                              CircuitBreakerStatePersistence statePersistence,
                                              FeignCircuitBreakerMetricsExporter metricsExporter) {
        FeignProperties.CircuitBreaker cb = properties.getCircuitBreaker();
        this.config = buildConfig(cb);
        this.statePersistence = statePersistence;
        this.metricsExporter = metricsExporter;
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
        this.statePersistence = null;
        this.metricsExporter = null;
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
        CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        CircuitBreakerState state = toState(cb.getState());
        stateCache.put(serviceName, state);

        if (state == CircuitBreakerState.OPEN && statePersistence != null) {
            statePersistence.persistState(serviceName, state);
        }

        return cb.tryAcquirePermission();
    }

    @Override
    public void recordSuccess(String serviceName, long elapsedTime) {
        CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        cb.onSuccess(elapsedTime, TimeUnit.MILLISECONDS);
        stateCache.put(serviceName, toState(cb.getState()));
        updateMetrics(serviceName, true, elapsedTime);
    }

    @Override
    public void recordFailure(String serviceName, long elapsedTime, Throwable throwable) {
        CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        cb.onError(elapsedTime, TimeUnit.MILLISECONDS, throwable);
        CircuitBreakerState newState = toState(cb.getState());
        stateCache.put(serviceName, newState);
        log.warn("[Resilience4jCircuitBreaker] 调用失败, service={}, cause={}", serviceName, throwable.getMessage());
        updateMetrics(serviceName, false, elapsedTime);

        if (statePersistence != null && newState == CircuitBreakerState.OPEN) {
            statePersistence.persistState(serviceName, newState);
        }
    }

    @Override
    public CircuitBreakerState getState(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb == null) {
            if (statePersistence != null) {
                CircuitBreakerState restored = statePersistence.restoreState(serviceName);
                if (restored != null) {
                    return restored;
                }
            }
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
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb != null) {
            cb.reset();
        }
        stateCache.put(serviceName, CircuitBreakerState.CLOSED);
        metricsCache.remove(serviceName);
        if (statePersistence != null) {
            statePersistence.clearState(serviceName);
        }
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
        CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        return CircuitBreaker.decorateSupplier(cb, supplier).get();
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
        CircuitBreaker cb = getOrCreateCircuitBreaker(serviceName);
        try {
            return CircuitBreaker.decorateSupplier(cb, supplier).get();
        } catch (Exception e) {
            log.warn("[Resilience4jCircuitBreaker] 调用失败, service={}, 使用降级逻辑, cause={}", serviceName, e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 获取或创建指定服务的 Resilience4j CircuitBreaker 实例。
     *
     * <p>创建新实例时：
     * <ol>
     *   <li>尝试从 Redis 恢复之前的熔断状态</li>
     *   <li>自动注册 Micrometer 指标</li>
     *   <li>应用 per-client 配置覆盖（如有）</li>
     * </ol>
     */
    private CircuitBreaker getOrCreateCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, name -> {
            // 构建配置时应用 per-client 覆盖
            CircuitBreakerConfig cbConfig = buildConfig(properties.getCircuitBreaker(), name);
            CircuitBreaker cb = CircuitBreaker.of(toResource(name), cbConfig);

            if (statePersistence != null) {
                CircuitBreakerState restored = statePersistence.restoreState(name);
                if (restored == CircuitBreakerState.OPEN) {
                    cb.transitionToForcedOpenState();
                    log.info("[Resilience4jCircuitBreaker] 从 Redis 恢复熔断状态为 OPEN, service={}", name);
                }
            }

            if (metricsExporter != null) {
                metricsExporter.registerServiceMetrics(name);
            }

            return cb;
        });
    }

    private String toResource(String serviceName) {
        return RESOURCE_PREFIX + serviceName;
    }

    /**
     * 构建 CircuitBreaker 配置，应用 per-client 覆盖。
     *
     * @param cb          全局熔断器配置
     * @param serviceName 服务名称（用于查找 per-client 覆盖）
     * @return 合并后的 CircuitBreakerConfig
     */
    private CircuitBreakerConfig buildConfig(FeignProperties.CircuitBreaker cb, String serviceName) {
        // 查找 per-client 配置覆盖
        FeignProperties.CircuitBreakerClientConfig clientOverride =
                cb.getClientConfig() != null ? cb.getClientConfig().get(serviceName) : null;

        float failureRateThreshold = clientOverride != null && clientOverride.getFailureRateThreshold() != null
                ? clientOverride.getFailureRateThreshold() : cb.getFailureRateThreshold();
        float slowCallRateThreshold = clientOverride != null && clientOverride.getSlowCallRateThreshold() != null
                ? clientOverride.getSlowCallRateThreshold() : cb.getSlowCallRateThreshold();
        int slowCallDurationThreshold = clientOverride != null && clientOverride.getSlowCallDurationThreshold() != null
                ? clientOverride.getSlowCallDurationThreshold() : cb.getSlowCallDurationThreshold();
        int slidingWindowSize = clientOverride != null && clientOverride.getSlidingWindowSize() != null
                ? clientOverride.getSlidingWindowSize() : cb.getSlidingWindowSize();
        int waitDurationInOpenState = clientOverride != null && clientOverride.getWaitDurationInOpenState() != null
                ? clientOverride.getWaitDurationInOpenState() : cb.getWaitDurationInOpenState();
        int permittedHalfOpen = clientOverride != null && clientOverride.getPermittedNumberOfCallsInHalfOpenState() != null
                ? clientOverride.getPermittedNumberOfCallsInHalfOpenState() : cb.getPermittedNumberOfCallsInHalfOpenState();
        SlidingWindowType windowType;
        if (clientOverride != null && clientOverride.getSlidingWindowType() != null) {
            windowType = "TIME_BASED".equalsIgnoreCase(clientOverride.getSlidingWindowType())
                    ? SlidingWindowType.TIME_BASED : SlidingWindowType.COUNT_BASED;
        } else {
            windowType = "TIME_BASED".equalsIgnoreCase(cb.getSlidingWindowType())
                    ? SlidingWindowType.TIME_BASED : SlidingWindowType.COUNT_BASED;
        }

        return CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallDurationThreshold))
                .permittedNumberOfCallsInHalfOpenState(permittedHalfOpen)
                .slidingWindowSize(slidingWindowSize)
                .slidingWindowType(windowType)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationInOpenState))
                .build();
    }

    private CircuitBreakerState toState(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> CircuitBreakerState.CLOSED;
            case OPEN -> CircuitBreakerState.OPEN;
            case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
            case DISABLED -> CircuitBreakerState.DISABLED;
            case FORCED_OPEN -> CircuitBreakerState.FORCED_OPEN;
            case METRICS_ONLY -> CircuitBreakerState.CLOSED;
        };
    }

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
        // 指数移动平均计算平均耗时，避免历史数据无限累积导致均值僵化
        long prevAvg = m.getAverageDuration();
        if (prevAvg == 0L) {
            m.setAverageDuration(elapsedTime);
        } else {
            // EMA 权重 0.1，新样本占 10%，历史均值占 90%
            m.setAverageDuration(prevAvg + (elapsedTime - prevAvg) / 10);
        }
        // 原子更新最大耗时
        m.updateMaxDuration(elapsedTime);
        long total = m.getTotalCalls();
        if (total > 0) {
            m.setFailureRate((double) m.getFailedCalls() / total * 100.0);
            m.setSlowCallRate((double) m.getSlowCalls() / total * 100.0);
        }
    }
}
