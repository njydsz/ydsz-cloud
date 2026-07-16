package com.njydsz.common.notify.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.notify.enums.NotifyChannel;

/**
 * 通知渠道熔断器（P0-3）
 *
 * <p>基于连续失败计数实现轻量级熔断保护，防止渠道故障时持续尝试导致资源浪费。
 *
 * <p><b>熔断状态机：</b>
 * <ul>
 *   <li><b>CLOSED</b>（正常）：请求正常通过，记录失败次数</li>
 *   <li><b>OPEN</b>（熔断）：连续失败超过阈值，拒绝所有请求，等待恢复时间</li>
 *   <li><b>HALF_OPEN</b>（半开）：恢复时间到达后，放行单个探测请求；
 *       探测成功则回到 CLOSED，探测失败则重新进入 OPEN</li>
 * </ul>
 *
 * <p><b>线程安全（P1-4）：</b>使用 {@link AtomicReference} + CAS 操作确保状态转换原子性，
 * HALF_OPEN 状态仅允许单个探测请求通过。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NotifyCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(NotifyCircuitBreaker.class);

    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_RECOVERY_TIMEOUT_MS = 60_000L;

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final NotifyChannel channel;
    private final int failureThreshold;
    private final long recoveryTimeoutMs;

    /** 使用 AtomicReference 确保状态转换原子性（P1-4） */
    private final AtomicReference<State> stateRef = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;
    private volatile long openedAt = 0;

    /** HALF_OPEN 状态下的探测许可数，确保仅单个请求通过 */
    private final AtomicInteger halfOpenPermits = new AtomicInteger(0);

    public NotifyCircuitBreaker(NotifyChannel channel) {
        this(channel, DEFAULT_FAILURE_THRESHOLD, DEFAULT_RECOVERY_TIMEOUT_MS);
    }

    public NotifyCircuitBreaker(NotifyChannel channel, int failureThreshold, long recoveryTimeoutMs) {
        this.channel = channel;
        this.failureThreshold = failureThreshold > 0 ? failureThreshold : DEFAULT_FAILURE_THRESHOLD;
        this.recoveryTimeoutMs = recoveryTimeoutMs > 0 ? recoveryTimeoutMs : DEFAULT_RECOVERY_TIMEOUT_MS;
    }

    /**
     * 尝试获取熔断器许可
     *
     * <p>线程安全：使用 CAS 确保 OPEN→HALF_OPEN 转换仅由一个线程执行。
     *
     * @return true 表示允许通过，false 表示被熔断拒绝
     */
    public boolean tryAcquire() {
        State currentState = stateRef.get();
        if (currentState == State.CLOSED) {
            return true;
        }
        if (currentState == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now - openedAt >= recoveryTimeoutMs) {
                // CAS: OPEN → HALF_OPEN，仅一个线程成功转换
                if (stateRef.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("[NotifyCircuitBreaker] 渠道[{}]熔断器进入半开状态，尝试探测", channel.getName());
                    halfOpenPermits.set(1);
                    return true;
                }
                // CAS 失败，其他线程已转换，检查是否仍有探测许可
                return stateRef.get() == State.HALF_OPEN && halfOpenPermits.getAndDecrement() > 0;
            }
            return false;
        }
        // HALF_OPEN：仅允许有许可的请求通过
        return halfOpenPermits.getAndDecrement() > 0;
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        State old = stateRef.getAndSet(State.CLOSED);
        if (old != State.CLOSED) {
            log.info("[NotifyCircuitBreaker] 渠道[{}]熔断器恢复，切换到 CLOSED 状态", channel.getName());
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        lastFailureTime = System.currentTimeMillis();
        int failures = consecutiveFailures.incrementAndGet();

        State currentState = stateRef.get();
        if (currentState == State.HALF_OPEN) {
            // 半开探测失败，CAS 重新熔断
            if (stateRef.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openedAt = System.currentTimeMillis();
                log.warn("[NotifyCircuitBreaker] 渠道[{}]半开探测失败，重新熔断", channel.getName());
            }
            return;
        }

        if (failures >= failureThreshold && currentState == State.CLOSED) {
            if (stateRef.compareAndSet(State.CLOSED, State.OPEN)) {
                openedAt = System.currentTimeMillis();
                log.error("[NotifyCircuitBreaker] 渠道[{}]连续失败 {} 次达到阈值，熔断器开启，恢复等待 {}ms",
                        channel.getName(), failures, recoveryTimeoutMs);
            }
        }
    }

    public State getState() {
        return stateRef.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public NotifyChannel getChannel() {
        return channel;
    }

    public boolean isOpen() {
        return stateRef.get() != State.CLOSED;
    }
}
