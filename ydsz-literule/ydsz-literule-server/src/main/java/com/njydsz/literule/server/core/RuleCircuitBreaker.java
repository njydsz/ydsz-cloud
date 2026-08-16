package com.njydsz.literule.server.core;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

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
 * <p>滑动窗口（P2-5）：基于环形缓冲区记录最近 {@link #windowSize} 次评估结果，
 * 仅计算窗口内的错误率，避免历史成功稀释近期突发错误导致熔断器永不触发。
 * 对标 Resilience4j {@code BitSet} 滑动窗口实现。
 *
 * @since 1.0.0
 * @author ydsz-team
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

    /**
     * 单个规则的熔断器状态
     *
     * <p>滑动窗口通过 {@code synchronized} 保护，原因：
     * <ul>
     *   <li>单规则评估的 {@code recordResult} 调用频率低（每次规则评估一次）</li>
     *   <li>窗口操作涉及读-改-写（移除旧值、写入新值、更新计数器），需要原子性</li>
     *   <li>相对于 ConcurrentHashMap 的 compute 原语，synchronized 更直观且无死锁风险</li>
     * </ul>
     */
    private static class BreakerState {
        final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        final AtomicLong openedAt = new AtomicLong(0);

        /** 滑动窗口：true=失败，false=成功 */
        final boolean[] window;
        /** 下一个写入位置（环形） */
        int head = 0;
        /** 窗口内已写入的记录数（≤ window.length） */
        int count = 0;
        /** 窗口内失败数 */
        int failures = 0;

        BreakerState(int windowSize) {
            this.window = new boolean[windowSize];
        }

        /** 记录一次评估结果到滑动窗口 */
        synchronized void record(boolean success) {
            // 窗口已满时，移除最旧记录
            if (count == window.length) {
                if (window[head]) {
                    failures--;
                }
            } else {
                count++;
            }
            window[head] = !success;
            if (!success) {
                failures++;
            }
            head = (head + 1) % window.length;
        }

        /** 当前窗口错误率（0~1.0）；样本不足返回 -1 */
        synchronized double errorRate(int minSamples) {
            if (count < minSamples) {
                return -1;
            }
            return (double) failures / count;
        }

        /** 重置滑动窗口（HALF_OPEN → CLOSED 时调用） */
        synchronized void resetWindow() {
            head = 0;
            count = 0;
            failures = 0;
            Arrays.fill(window, false);
        }
    }

    private final double errorRateThreshold;
    private final int minEvaluations;
    private final long openStateMs;
    /** 滑动窗口大小（= minEvaluations，仅看最近 N 次评估） */
    private final int windowSize;

    /** 每个规则一个独立熔断器 */
    private final ConcurrentMap<String, BreakerState> breakers = new ConcurrentHashMap<>();

    /**
     * 构造熔断器
     *
     * @param errorRateThreshold 错误率阈值（0~1.0）
     * @param minEvaluations     最小评估次数（达到后才计算错误率；同时作为滑动窗口大小）
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
        this.windowSize = this.minEvaluations;
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
        BreakerState state = breakers.computeIfAbsent(ruleCode, k -> new BreakerState(windowSize));

        State current = state.state.get();
        if (current == State.HALF_OPEN) {
            if (success) {
                // HALF_OPEN 成功 → CLOSED，重置滑动窗口
                state.state.set(State.CLOSED);
                state.resetWindow();
                log.info("[LiteRule-Breaker] 规则 {} 熔断器已恢复 CLOSED", ruleCode);
            } else {
                // HALF_OPEN 失败 → 重新 OPEN
                state.state.set(State.OPEN);
                state.openedAt.set(System.currentTimeMillis());
                log.warn("[LiteRule-Breaker] 规则 {} 熔断器重新 OPEN（HALF_OPEN 评估失败）", ruleCode);
            }
            return;
        }

        // 记录到滑动窗口（CLOSED 状态）
        state.record(success);

        if (current == State.CLOSED) {
            double errorRate = state.errorRate(minEvaluations);
            if (errorRate >= 0 && errorRate >= errorRateThreshold) {
                if (state.state.compareAndSet(State.CLOSED, State.OPEN)) {
                    state.openedAt.set(System.currentTimeMillis());
                    log.warn("[LiteRule-Breaker] 规则 {} 熔断器 OPEN（滑动窗口错误率 {}%, 窗口={}/{})",
                            ruleCode, String.format("%.2f", errorRate * 100),
                            state.failures, state.count);
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
