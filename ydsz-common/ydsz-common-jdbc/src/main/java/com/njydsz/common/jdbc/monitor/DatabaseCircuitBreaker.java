package com.njydsz.common.jdbc.monitor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据库熔断器（自研实现，三状态机）
 *
 * <p>基于 CLOSED → OPEN → HALF_OPEN 三状态机模型，保护数据库免受故障扩散：
 * <ul>
 *   <li><b>CLOSED</b> — 正常工作，累计失败次数达到阈值后转 OPEN</li>
 *   <li><b>OPEN</b> — 拒绝所有请求，持续指定时间后转 HALF_OPEN</li>
 *   <li><b>HALF_OPEN</b> — 放行有限探测请求，成功达标转 CLOSED，失败转 OPEN</li>
 * </ul>
 *
 * <p>提供 {@link #tryAcquire()} / {@link #recordSuccess()} / {@link #recordFailure()} 三个核心方法，
 * 由 {@link com.njydsz.common.jdbc.interceptor.CircuitBreakerInterceptor} 在拦截器链中调用。
 *
 * <p>线程安全：所有状态转换基于 {@link AtomicReference} 和 AtomicInteger。
 *
 * @author ydsz-team
 * @since 1.8.0
 * @see com.njydsz.common.jdbc.interceptor.CircuitBreakerInterceptor
 * @see com.njydsz.common.jdbc.config.DatabaseCircuitBreakerAutoConfiguration
 */
@Slf4j
public class DatabaseCircuitBreaker {

    /** 熔断器状态枚举 */
    public enum State {
        /** 正常工作 */
        CLOSED,
        /** 熔断打开（拒绝请求） */
        OPEN,
        /** 半开探测 */
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long openDurationMillis;
    private final int halfOpenProbeSize;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private volatile Instant openedAt;

    /**
     * 构造数据库熔断器
     *
     * @param failureThreshold  连续失败次数阈值（触发 OPEN）
     * @param openDurationMillis OPEN 持续时间毫秒（到期转 HALF_OPEN）
     * @param halfOpenProbeSize HALF_OPEN 成功探测次数阈值（达标转 CLOSED）
     */
    public DatabaseCircuitBreaker(int failureThreshold, long openDurationMillis, int halfOpenProbeSize) {
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = openDurationMillis;
        this.halfOpenProbeSize = halfOpenProbeSize;
    }

    /**
     * 尝试获取执行许可
     *
     * @return true 表示允许执行（CLOSED 或 HALF_OPEN 探测）；false 表示 OPEN 状态拒绝执行
     */
    public boolean tryAcquire() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.HALF_OPEN) {
            // 半开状态放行（由调用方控制探测频率）
            return true;
        }
        // OPEN 状态：检查是否可以转 HALF_OPEN
        if (current == State.OPEN) {
            if (openedAt != null
                    && Duration.between(openedAt, Instant.now()).toMillis() >= openDurationMillis) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenSuccessCount.set(0);
                    log.info("数据库熔断器从 OPEN 转为 HALF_OPEN");
                }
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * 记录执行成功
     */
    public void recordSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            int successCount = halfOpenSuccessCount.incrementAndGet();
            if (successCount >= halfOpenProbeSize) {
                state.set(State.CLOSED);
                failureCount.set(0);
                log.info("数据库熔断器从 HALF_OPEN 转为 CLOSED（成功探测 {} 次）", successCount);
            }
        } else if (current == State.CLOSED) {
            // 成功时重置失败计数（避免历史失败累积）
            failureCount.set(0);
        }
    }

    /**
     * 记录执行失败
     */
    public void recordFailure() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            // 半开状态任何失败直接回 OPEN
            state.set(State.OPEN);
            openedAt = Instant.now();
            log.warn("数据库熔断器从 HALF_OPEN 回退到 OPEN（探测失败）");
        } else if (current == State.CLOSED) {
            int count = failureCount.incrementAndGet();
            if (count >= failureThreshold) {
                state.set(State.OPEN);
                openedAt = Instant.now();
                log.warn("数据库熔断器从 CLOSED 转为 OPEN（连续失败 {} 次）", count);
            }
        }
    }

    /**
     * 获取当前状态
     *
     * @return 当前状态枚举
     */
    public State getState() {
        return state.get();
    }

    /**
     * 绑定 Micrometer 指标
     *
     * @param registry Micrometer 注册表
     */
    public void bindTo(MeterRegistry registry) {
        if (registry == null) {
            return;
        }
        Gauge.builder("jdbc.circuit.breaker.state", state,
                        s -> s.get().ordinal())
                .description("Database circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .register(registry);
    }
}
