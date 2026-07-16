package com.njydsz.common.jdbc.monitor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 数据库操作熔断器
 *
 * <p>基于滑动窗口的轻量级熔断器，在数据库连续异常时自动切断请求，
 * 避免线程池耗尽和级联故障。无需引入 Resilience4j 外部依赖。
 *
 * <p>状态机：
 * <ul>
 *   <li>CLOSED — 正常状态，所有请求通过</li>
 *   <li>OPEN — 熔断状态，所有请求被快速拒绝</li>
 *   <li>HALF_OPEN — 半开状态，允许有限请求探测恢复</li>
 * </ul>
 *
 * <p>可观测性：通过 {@link #bindTo(MeterRegistry)} 暴露 Micrometer 指标：
 * <ul>
 *   <li>{@code dbc.circuitbreaker.state} Gauge — 熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN）</li>
 *   <li>{@code dbc.circuitbreaker.consecutive.failures} Gauge — 当前连续失败次数</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   jdbc:
 *     circuit-breaker:
 *       enabled: true
 *       failure-threshold: 10          # 连续失败次数阈值
 *       open-duration-millis: 30000    # 熔断持续时间（ms）
 *       half-open-probe-size: 3        # 半开探测请求数
 * </pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public class DatabaseCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCircuitBreaker.class);

    /** 连续失败次数阈值 */
    private final int failureThreshold;
    /** 熔断持续时间 */
    private final Duration openDuration;
    /** 半开探测请求数 */
    private final int halfOpenProbeSize;

    /** 当前连续失败次数 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    /** 半开状态下的探测请求计数 */
    private final AtomicInteger halfOpenProbeCount = new AtomicInteger(0);
    /** 熔断器状态 */
    private volatile State state = State.CLOSED;
    /** 熔断开始时间（纳秒） */
    private volatile long openedAtNanos = 0;

    /**
     * 构造数据库操作熔断器
     *
     * @param failureThreshold  连续失败次数阈值
     * @param openDurationMillis 熔断持续时间（毫秒）
     * @param halfOpenProbeSize  半开探测请求数
     */
    public DatabaseCircuitBreaker(int failureThreshold, long openDurationMillis, int halfOpenProbeSize) {
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofMillis(openDurationMillis);
        this.halfOpenProbeSize = halfOpenProbeSize;
    }

    /**
     * 尝试获取执行许可
     *
     * @return true 表示允许执行，false 表示被熔断拒绝
     */
    public boolean tryAcquire() {
        State currentState = state;
        switch (currentState) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查是否到了半开时间
                if (System.nanoTime() - openedAtNanos > openDuration.toNanos()) {
                    synchronized (this) {
                        if (state == State.OPEN) {
                            state = State.HALF_OPEN;
                            halfOpenProbeCount.set(0);
                            log.info("数据库熔断器进入 HALF_OPEN 状态，开始探测");
                        }
                    }
                    return halfOpenProbeCount.incrementAndGet() <= halfOpenProbeSize;
                }
                return false;
            case HALF_OPEN:
                return halfOpenProbeCount.incrementAndGet() <= halfOpenProbeSize;
            default:
                return true;
        }
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            synchronized (this) {
                if (state == State.HALF_OPEN) {
                    state = State.CLOSED;
                    consecutiveFailures.set(0);
                    log.info("数据库熔断器恢复为 CLOSED 状态");
                }
            }
        } else if (state == State.CLOSED) {
            consecutiveFailures.set(0);
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state == State.CLOSED && failures >= failureThreshold) {
            synchronized (this) {
                if (state == State.CLOSED && consecutiveFailures.get() >= failureThreshold) {
                    state = State.OPEN;
                    openedAtNanos = System.nanoTime();
                    log.error("数据库熔断器进入 OPEN 状态，连续失败 {} 次，熔断持续 {}ms",
                            failures, openDuration.toMillis());
                }
            }
        } else if (state == State.HALF_OPEN) {
            synchronized (this) {
                if (state == State.HALF_OPEN) {
                    state = State.OPEN;
                    openedAtNanos = System.nanoTime();
                    consecutiveFailures.set(failureThreshold);
                    log.error("数据库熔断器探测失败，重新进入 OPEN 状态，熔断持续 {}ms", openDuration.toMillis());
                }
            }
        }
    }

    /**
     * 获取当前熔断器状态
     *
     * @return 熔断器状态
     */
    public State getState() {
        return state;
    }

    /**
     * 获取当前连续失败次数
     *
     * @return 连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 将熔断器指标绑定到 Micrometer MeterRegistry
     *
     * @param registry Micrometer 指标注册表
     */
    public void bindTo(MeterRegistry registry) {
        if (registry == null) {
            return;
        }
        Gauge.builder("dbc.circuitbreaker.state", () -> state.ordinal())
                .description("Database circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .register(registry);
        Gauge.builder("dbc.circuitbreaker.consecutive.failures", consecutiveFailures::get)
                .description("Database circuit breaker consecutive failure count")
                .register(registry);
    }

    /**
     * 熔断器状态枚举
     */
    public enum State {
        /** 正常状态 */
        CLOSED,
        /** 熔断状态 */
        OPEN,
        /** 半开探测状态 */
        HALF_OPEN
    }
}
