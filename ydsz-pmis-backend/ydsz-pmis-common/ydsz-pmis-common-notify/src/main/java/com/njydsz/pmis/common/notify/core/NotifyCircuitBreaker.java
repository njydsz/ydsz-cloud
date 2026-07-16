package com.njydsz.pmis.common.notify.core;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.notify.enums.NotifyChannel;

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
 * <p><b>配置参数：</b>
 * <ul>
 *   <li>{@code failureThreshold} — 连续失败阈值（默认 5 次）</li>
 *   <li>{@code recoveryTimeoutMs} — 熔断恢复等待时间（默认 60 秒）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public class NotifyCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(NotifyCircuitBreaker.class);

    /** 默认连续失败阈值 */
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** 默认恢复等待时间（毫秒） */
    private static final long DEFAULT_RECOVERY_TIMEOUT_MS = 60_000L;

    /** 熔断状态 */
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final NotifyChannel channel;
    private final int failureThreshold;
    private final long recoveryTimeoutMs;

    private volatile State state = State.CLOSED;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;
    private volatile long openedAt = 0;

    /**
     * 使用默认参数创建熔断器
     *
     * @param channel 通知渠道
     */
    public NotifyCircuitBreaker(NotifyChannel channel) {
        this(channel, DEFAULT_FAILURE_THRESHOLD, DEFAULT_RECOVERY_TIMEOUT_MS);
    }

    /**
     * 创建熔断器
     *
     * @param channel           通知渠道
     * @param failureThreshold  连续失败阈值
     * @param recoveryTimeoutMs 恢复等待时间（毫秒）
     */
    public NotifyCircuitBreaker(NotifyChannel channel, int failureThreshold, long recoveryTimeoutMs) {
        this.channel = channel;
        this.failureThreshold = failureThreshold > 0 ? failureThreshold : DEFAULT_FAILURE_THRESHOLD;
        this.recoveryTimeoutMs = recoveryTimeoutMs > 0 ? recoveryTimeoutMs : DEFAULT_RECOVERY_TIMEOUT_MS;
    }

    /**
     * 尝试获取熔断器许可
     *
     * <p>当熔断器处于 OPEN 状态时，如果恢复时间已到则切换到 HALF_OPEN。
     *
     * @return true 表示允许通过，false 表示被熔断拒绝
     */
    public boolean tryAcquire() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now - openedAt >= recoveryTimeoutMs) {
                state = State.HALF_OPEN;
                log.info("[NotifyCircuitBreaker] 渠道[{}]熔断器进入半开状态，尝试探测", channel.getName());
                return true;
            }
            return false;
        }
        // HALF_OPEN：仅允许单个探测请求
        return true;
    }

    /**
     * 记录成功
     *
     * <p>重置失败计数，将状态切换回 CLOSED。
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        if (state != State.CLOSED) {
            log.info("[NotifyCircuitBreaker] 渠道[{}]熔断器恢复，切换到 CLOSED 状态", channel.getName());
            state = State.CLOSED;
        }
    }

    /**
     * 记录失败
     *
     * <p>增加失败计数，达到阈值后切换到 OPEN 状态。
     */
    public void recordFailure() {
        lastFailureTime = System.currentTimeMillis();
        int failures = consecutiveFailures.incrementAndGet();

        if (state == State.HALF_OPEN) {
            // 半开状态下探测失败，重新熔断
            state = State.OPEN;
            openedAt = System.currentTimeMillis();
            log.warn("[NotifyCircuitBreaker] 渠道[{}]半开探测失败，重新熔断", channel.getName());
            return;
        }

        if (failures >= failureThreshold && state == State.CLOSED) {
            state = State.OPEN;
            openedAt = System.currentTimeMillis();
            log.error("[NotifyCircuitBreaker] 渠道[{}]连续失败 {} 次达到阈值，熔断器开启，恢复等待 {}ms",
                    channel.getName(), failures, recoveryTimeoutMs);
        }
    }

    /**
     * 获取当前熔断状态
     *
     * @return 熔断状态
     */
    public State getState() {
        return state;
    }

    /**
     * 获取连续失败次数
     *
     * @return 连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 获取通知渠道
     *
     * @return 通知渠道
     */
    public NotifyChannel getChannel() {
        return channel;
    }

    /**
     * 判断熔断器是否处于熔断状态
     *
     * @return true 表示处于 OPEN 或 HALF_OPEN 状态
     */
    public boolean isOpen() {
        return state != State.CLOSED;
    }
}
