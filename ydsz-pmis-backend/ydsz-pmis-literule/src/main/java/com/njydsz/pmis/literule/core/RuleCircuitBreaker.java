package com.njydsz.pmis.literule.core;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 规则熔断器（基于错误率的滑动窗口熔断）
 *
 * <p>每个规则编码独立维护一个熔断器，按以下策略工作：
 * <ul>
 *   <li>评估次数达到 {@link #minEvaluations} 后开始计算错误率</li>
 *   <li>错误率超过 {@link #errorRateThreshold} 时进入 OPEN 状态，拒绝评估</li>
 *   <li>OPEN 状态持续 {@link #openStateMs} 毫秒后转为 HALF_OPEN，允许试探性评估</li>
 *   <li>HALF_OPEN 下评估成功则转为 CLOSED，失败则继续 OPEN</li>
 * </ul>
 *
 * <p>滑动窗口：基于总评估次数与总错误次数的累计比率（简化实现，避免窗口队列开销）。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class RuleCircuitBreaker {

    /** 熔断状态 */
    public enum State {
        /** 关闭：正常评估 */
        CLOSED,
        /** 打开：拒绝评估 */
        OPEN,
        /** 半开：试探性评估 */
        HALF_OPEN
    }

    /** 单个规则的熔断器状态 */
    private static class BreakerState {
        final AtomicLong totalEvaluations = new AtomicLong(0);
        final AtomicLong totalErrors = new AtomicLong(0);
        final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        final AtomicLong openedAt = new AtomicLong(0);
    }

    private final double errorRateThreshold;
    private final int minEvaluations;
    private final long openStateMs;

    /** 每个规则一个独立熔断器 */
    private final ConcurrentMap<String, BreakerState> breakers = new ConcurrentHashMap<>();

    /**
     * 构造熔断器
     *
     * @param errorRateThreshold 错误率阈值（0~1.0）
     * @param minEvaluations     最小评估次数（达到后才计算错误率）
     * @param openStateMs        OPEN 状态持续时间（毫秒）
     */
    public RuleCircuitBreaker(double errorRateThreshold, int minEvaluations, long openStateMs) {
        if (errorRateThreshold <= 0 || errorRateThreshold > 1) {
            throw new IllegalArgumentException("errorRateThreshold 必须在 (0, 1] 区间");
        }
        if (openStateMs <= 0) {
            throw new IllegalArgumentException("openStateMs 必须大于 0");
        }
        this.errorRateThreshold = errorRateThreshold;
        this.minEvaluations = Math.max(1, minEvaluations);
        this.openStateMs = openStateMs;
    }

    /**
     * 判断规则是否允许评估（未被熔断）
     *
     * @param ruleCode 规则编码
     * @return true=允许评估；false=已被熔断
     */
    public boolean allowEvaluate(String ruleCode) {
        BreakerState state = breakers.get(ruleCode);
        if (state == null) {
            return true;
        }
        State current = state.state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            // 检查是否到了 HALF_OPEN 转换时间
            long openedAt = state.openedAt.get();
            if (System.currentTimeMillis() - openedAt >= openStateMs) {
                if (state.state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.warn("[LiteRule-Breaker] 规则 {} 熔断器进入 HALF_OPEN 状态", ruleCode);
                }
                return true;
            }
            return false;
        }
        // HALF_OPEN：允许试探性评估
        return true;
    }

    /**
     * 记录评估结果
     *
     * @param ruleCode  规则编码
     * @param success   是否成功（false 表示异常）
     */
    public void recordResult(String ruleCode, boolean success) {
        BreakerState state = breakers.computeIfAbsent(ruleCode, k -> new BreakerState());
        state.totalEvaluations.incrementAndGet();
        if (!success) {
            state.totalErrors.incrementAndGet();
        }

        State current = state.state.get();
        if (current == State.HALF_OPEN) {
            if (success) {
                // HALF_OPEN 成功 → CLOSED，重置计数器
                state.state.set(State.CLOSED);
                state.totalEvaluations.set(0);
                state.totalErrors.set(0);
                log.info("[LiteRule-Breaker] 规则 {} 熔断器已恢复 CLOSED", ruleCode);
            } else {
                // HALF_OPEN 失败 → 重新 OPEN
                state.state.set(State.OPEN);
                state.openedAt.set(System.currentTimeMillis());
                log.warn("[LiteRule-Breaker] 规则 {} 熔断器重新 OPEN（HALF_OPEN 评估失败）", ruleCode);
            }
            return;
        }

        if (current == State.CLOSED) {
            long total = state.totalEvaluations.get();
            if (total >= minEvaluations) {
                double errorRate = (double) state.totalErrors.get() / total;
                if (errorRate >= errorRateThreshold) {
                    if (state.state.compareAndSet(State.CLOSED, State.OPEN)) {
                        state.openedAt.set(System.currentTimeMillis());
                        log.warn("[LiteRule-Breaker] 规则 {} 熔断器 OPEN（错误率 {:.2f}%, {}/{})",
                                ruleCode, errorRate * 100, state.totalErrors.get(), total);
                    }
                }
            }
        }
    }

    /**
     * 查询规则当前熔断状态
     *
     * @param ruleCode 规则编码
     * @return 状态；规则未被评估过返回 CLOSED
     */
    public State getState(String ruleCode) {
        BreakerState state = breakers.get(ruleCode);
        return state == null ? State.CLOSED : state.state.get();
    }

    /**
     * 重置规则熔断器
     *
     * @param ruleCode 规则编码
     */
    public void reset(String ruleCode) {
        breakers.remove(ruleCode);
    }

    /**
     * 重置全部熔断器
     */
    public void resetAll() {
        breakers.clear();
    }
}
