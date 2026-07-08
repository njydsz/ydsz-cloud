package com.njydsz.pmis.agent.engine.llm;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM Provider 轻量级熔断器（P1-2 落地）。
 *
 * <p>当某个 Provider 连续失败达到阈值时，熔断器开启（OPEN），
 * 在冷却期内跳过该 Provider 的调用，避免持续请求已宕机的 LLM 服务。
 *
 * <p>状态机：
 * <ul>
 *   <li><b>CLOSED</b>：正常调用，记录失败次数</li>
 *   <li><b>OPEN</b>：熔断中，拒绝调用，等待冷却期过后进入 HALF_OPEN</li>
 *   <li><b>HALF_OPEN</b>：放行一次试探调用，成功则恢复 CLOSED，失败则重新 OPEN</li>
 * </ul>
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap} + {@link AtomicInteger}，
 * 无锁设计，适用于高并发场景。
 *
 * <p>注：生产环境可升级为 Resilience4j / Sentinel 熔断器，此为轻量内置实现。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-2)
 */
@Slf4j
public class LlmCircuitBreaker {

    /** 默认失败阈值（连续失败次数达到此值时熔断） */
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** 默认冷却时间（毫秒） */
    private static final long DEFAULT_COOLDOWN_MS = 30_000L;

    /** 默认半开试探次数 */
    private static final int DEFAULT_HALF_OPEN_TRIALS = 1;

    /** 每个 Provider 的熔断状态 */
    private final ConcurrentHashMap<String, BreakerState> states = new ConcurrentHashMap<>();

    /** 失败阈值 */
    private final int failureThreshold;

    /** 冷却时间（毫秒） */
    private final long cooldownMs;

    /**
     * 使用默认配置构造熔断器。
     */
    public LlmCircuitBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MS);
    }

    /**
     * 自定义配置构造熔断器。
     *
     * @param failureThreshold 失败阈值
     * @param cooldownMs       冷却时间（毫秒）
     */
    public LlmCircuitBreaker(int failureThreshold, long cooldownMs) {
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
    }

    /**
     * 判断指定 Provider 是否允许调用（熔断器是否闭合或半开）。
     *
     * @param providerName Provider 名称
     * @return true 表示允许调用（CLOSED 或 HALF_OPEN）；false 表示熔断中（OPEN）
     */
    public boolean allowCall(String providerName) {
        BreakerState state = states.computeIfAbsent(providerName, k -> new BreakerState());
        long now = System.currentTimeMillis();

        synchronized (state) {
            switch (state.status) {
                case CLOSED:
                    return true;
                case OPEN:
                    // 检查冷却期是否已过
                    if (now - state.openedAt >= cooldownMs) {
                        state.status = Status.HALF_OPEN;
                        state.halfOpenTrials.set(0);
                        log.info("[CircuitBreaker] {} 熔断器进入 HALF_OPEN 状态", providerName);
                        return true;
                    }
                    return false;
                case HALF_OPEN:
                    // 半开状态只允许有限次试探
                    return state.halfOpenTrials.get() < DEFAULT_HALF_OPEN_TRIALS;
                default:
                    return true;
            }
        }
    }

    /**
     * 记录成功：重置失败计数，恢复 CLOSED 状态。
     *
     * @param providerName Provider 名称
     */
    public void recordSuccess(String providerName) {
        BreakerState state = states.get(providerName);
        if (state == null) return;
        synchronized (state) {
            if (state.status == Status.HALF_OPEN) {
                state.status = Status.CLOSED;
                state.failures.set(0);
                log.info("[CircuitBreaker] {} 熔断器恢复 CLOSED（半开试探成功）", providerName);
            } else {
                state.failures.set(0);
            }
        }
    }

    /**
     * 记录失败：增加失败计数，达到阈值时熔断。
     *
     * @param providerName Provider 名称
     */
    public void recordFailure(String providerName) {
        BreakerState state = states.computeIfAbsent(providerName, k -> new BreakerState());
        synchronized (state) {
            if (state.status == Status.HALF_OPEN) {
                // 半开状态失败：重新熔断
                state.status = Status.OPEN;
                state.openedAt = System.currentTimeMillis();
                log.warn("[CircuitBreaker] {} 半开试探失败，重新熔断 (OPEN)", providerName);
                return;
            }
            int count = state.failures.incrementAndGet();
            if (count >= failureThreshold && state.status == Status.CLOSED) {
                state.status = Status.OPEN;
                state.openedAt = System.currentTimeMillis();
                log.warn("[CircuitBreaker] {} 连续失败 {} 次，熔断器开启 (OPEN)，冷却 {}ms",
                        providerName, count, cooldownMs);
            }
        }
    }

    /**
     * 获取指定 Provider 的当前状态（用于监控/健康检查）。
     *
     * @param providerName Provider 名称
     * @return 状态名称（CLOSED / OPEN / HALF_OPEN）
     */
    public String getState(String providerName) {
        BreakerState state = states.get(providerName);
        if (state == null) return "CLOSED";
        synchronized (state) {
            return state.status.name();
        }
    }

    /**
     * 重置指定 Provider 的熔断状态（用于手动恢复）。
     *
     * @param providerName Provider 名称
     */
    public void reset(String providerName) {
        BreakerState state = states.get(providerName);
        if (state != null) {
            synchronized (state) {
                state.status = Status.CLOSED;
                state.failures.set(0);
                state.halfOpenTrials.set(0);
            }
            log.info("[CircuitBreaker] {} 熔断器已手动重置", providerName);
        }
    }

    // ==================== 内部类 ====================

    /** 熔断状态枚举 */
    private enum Status {
        CLOSED, OPEN, HALF_OPEN
    }

    /** 每个 Provider 的熔断状态 */
    private static class BreakerState {
        volatile Status status = Status.CLOSED;
        final AtomicInteger failures = new AtomicInteger(0);
        final AtomicInteger halfOpenTrials = new AtomicInteger(0);
        volatile long openedAt = 0L;
    }
}
