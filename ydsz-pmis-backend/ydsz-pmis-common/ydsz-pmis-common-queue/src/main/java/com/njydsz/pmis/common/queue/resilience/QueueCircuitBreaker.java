package com.njydsz.pmis.common.queue.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

/**
 * 队列熔断器
 *
 * <p>实现三态熔断器（CLOSED → OPEN → HALF_OPEN → CLOSED），
 * 在连续失败超过阈值时自动切断消息发送/消费，防止级联故障。
 *
 * <p>状态流转：
 * <ul>
 *   <li>CLOSED：正常工作，记录失败次数</li>
 *   <li>OPEN：熔断中，所有请求被快速拒绝</li>
 *   <li>HALF_OPEN：半开探测，允许单个请求试探恢复</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class QueueCircuitBreaker {

    /** 熔断器状态 */
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final long openStateTimeoutMillis;
    private final AtomicInteger consecutiveFailures;
    private final AtomicReference<State> state;
    private volatile long openedAt;

    /**
     * @param name                  熔断器名称（通常是 channel/topic）
     * @param failureThreshold      连续失败阈值，达到后触发熔断
     * @param openStateTimeoutMillis 熔断开启后的恢复等待时间（毫秒）
     */
    public QueueCircuitBreaker(String name, int failureThreshold, long openStateTimeoutMillis) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openStateTimeoutMillis = openStateTimeoutMillis;
        this.consecutiveFailures = new AtomicInteger(0);
        this.state = new AtomicReference<>(State.CLOSED);
        this.openedAt = 0L;
    }

    /**
     * 检查是否允许请求通过
     *
     * @return true 如果允许请求（CLOSED 或 HALF_OPEN 探测），false 如果熔断中（OPEN）
     */
    public boolean allowRequest() {
        State currentState = state.get();
        if (currentState == State.CLOSED) {
            return true;
        }
        if (currentState == State.OPEN) {
            long elapsed = System.currentTimeMillis() - openedAt;
            if (elapsed >= openStateTimeoutMillis) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("[CircuitBreaker-{}] 状态切换 OPEN → HALF_OPEN，开始探测", name);
                }
                return state.get() == State.HALF_OPEN;
            }
            return false;
        }
        // HALF_OPEN: 只允许一个探测请求
        return true;
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        State oldState = state.get();
        consecutiveFailures.set(0);
        if (oldState != State.CLOSED) {
            state.set(State.CLOSED);
            log.info("[CircuitBreaker-{}] 状态切换 {} → CLOSED，恢复正常", name, oldState);
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            state.set(State.OPEN);
            openedAt = System.currentTimeMillis();
            log.warn("[CircuitBreaker-{}] HALF_OPEN 探测失败，状态切换 → OPEN", name);
        } else if (currentState == State.CLOSED && failures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                openedAt = System.currentTimeMillis();
                log.warn("[CircuitBreaker-{}] 连续失败 {} 次，状态切换 CLOSED → OPEN", name, failures);
            }
        }
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        return state.get();
    }

    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 获取熔断器名称
     */
    public String getName() {
        return name;
    }

    /**
     * 强制重置为 CLOSED 状态
     */
    public void reset() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
        openedAt = 0L;
        log.info("[CircuitBreaker-{}] 手动重置为 CLOSED", name);
    }
}
