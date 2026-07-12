package com.njydsz.pmis.common.notify.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于滑动窗口的限流器
 *
 * <p>使用滑动窗口算法实现限流控制，适用于通知发送等场景的频率限制�?
 * 相比固定窗口，滑动窗口能更平滑地控制请求速率，避免窗口边界处的突发流量�?
 *
 * <p><b>使用示例�?/b>
 * <pre>{@code
 * // 创建一个每分钟最�?100 次请求的限流�?
 * SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(100, 60_000);
 *
 * if (limiter.tryAcquire()) {
 *     // 执行业务逻辑
 * } else {
 *     // 限流处理
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 2026-06-16
 */
@Slf4j
public class SlidingWindowRateLimiter {

    /**
     * 窗口大小（毫秒）
     */
    private final long windowSizeMillis;

    /**
     * 窗口内最大请求数
     */
    private final int maxRequests;

    /**
     * 子窗口数量（将窗口划分为多个子窗口，提高精度�?
     */
    private final int subWindowCount;

    /**
     * 子窗口大小（毫秒�?
     */
    private final long subWindowSizeMillis;

    /**
     * 子窗口计数数�?
     */
    private final AtomicInteger[] subWindowCounts;

    /**
     * 子窗口时间戳数组（记录每个子窗口的起始时间）
     */
    private final AtomicLong[] subWindowTimestamps;

    /**
     * 创建一个滑动窗口限流器
     *
     * @param maxRequests     窗口内最大请求数
     * @param windowSizeMillis 窗口大小（毫秒）
     */
    public SlidingWindowRateLimiter(int maxRequests, long windowSizeMillis) {
        this(maxRequests, windowSizeMillis, 10);
    }

    /**
     * 创建一个滑动窗口限流器
     *
     * @param maxRequests     窗口内最大请求数
     * @param windowSizeMillis 窗口大小（毫秒）
     * @param subWindowCount  子窗口数量（建议 10-20�?
     */
    public SlidingWindowRateLimiter(int maxRequests, long windowSizeMillis, int subWindowCount) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (windowSizeMillis <= 0) {
            throw new IllegalArgumentException("windowSizeMillis must be positive");
        }
        if (subWindowCount <= 0) {
            throw new IllegalArgumentException("subWindowCount must be positive");
        }

        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeMillis;
        this.subWindowCount = subWindowCount;
        this.subWindowSizeMillis = windowSizeMillis / subWindowCount;

        this.subWindowCounts = new AtomicInteger[subWindowCount];
        this.subWindowTimestamps = new AtomicLong[subWindowCount];

        for (int i = 0; i < subWindowCount; i++) {
            subWindowCounts[i] = new AtomicInteger(0);
            subWindowTimestamps[i] = new AtomicLong(0);
        }
    }

    /**
     * 同步锁，保证 tryAcquire �?check-then-act 原子�?
     */
    private final Object acquireLock = new Object();

    /**
     * 尝试获取一个许�?
     *
     * <p>使用 synchronized 保证"检查总数 + 增加计数"的原子性，
     * 避免高并发下多线程同时通过检查导致限流失效�?
     *
     * @return true 表示获取成功（未限流），false 表示被限�?
     */
    public boolean tryAcquire() {
        long now = Instant.now().toEpochMilli();
        int currentIndex = (int) ((now / subWindowSizeMillis) % subWindowCount);

        synchronized (acquireLock) {
            // 清理过期子窗�?
            cleanExpiredSubWindows(now, currentIndex);

            // 计算当前窗口内的总请求数
            int totalRequests = calculateTotalRequests(now);

            if (totalRequests >= maxRequests) {
                log.debug("[RateLimiter] 限流触发 | currentRequests={} | maxRequests={} | windowSize={}ms",
                        totalRequests, maxRequests, windowSizeMillis);
                return false;
            }

            // 增加当前子窗口计数（与上面的检查在同一同步块内，保证原子性）
            subWindowCounts[currentIndex].incrementAndGet();
            subWindowTimestamps[currentIndex].set(now);

            return true;
        }
    }

    /**
     * 获取当前窗口内的请求�?
     *
     * @return 当前请求�?
     */
    public int getCurrentRequestCount() {
        long now = Instant.now().toEpochMilli();
        return calculateTotalRequests(now);
    }

    /**
     * 获取限流器配置信�?
     *
     * @return 配置描述
     */
    public String getConfigInfo() {
        return String.format("maxRequests=%d, windowSize=%dms, subWindows=%d",
                maxRequests, windowSizeMillis, subWindowCount);
    }

    /**
     * 清理过期的子窗口
     */
    private void cleanExpiredSubWindows(long now, int currentIndex) {
        long windowStart = now - windowSizeMillis;

        for (int i = 0; i < subWindowCount; i++) {
            long timestamp = subWindowTimestamps[i].get();
            if (timestamp < windowStart) {
                // 子窗口已过期，重置计�?
                subWindowCounts[i].set(0);
                subWindowTimestamps[i].set(0);
            }
        }
    }

    /**
     * 计算当前窗口内的总请求数
     */
    private int calculateTotalRequests(long now) {
        long windowStart = now - windowSizeMillis;
        int total = 0;

        for (int i = 0; i < subWindowCount; i++) {
            long timestamp = subWindowTimestamps[i].get();
            if (timestamp >= windowStart) {
                total += subWindowCounts[i].get();
            }
        }

        return total;
    }
}
