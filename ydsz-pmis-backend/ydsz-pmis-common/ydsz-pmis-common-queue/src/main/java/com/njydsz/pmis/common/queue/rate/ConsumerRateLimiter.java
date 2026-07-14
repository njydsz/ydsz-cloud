package com.njydsz.pmis.common.queue.rate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于令牌桶的消费者限流器
 *
 * <p>使用令牌桶算法控制消息消费速率，防止下游系统被突发流量击垮。
 * 每个消费者实例持有独立的限流器，支持动态调整限流速率。
 *
 * <p><b>算法原理：</b>
 * <ul>
 *   <li>令牌以固定速率生成并放入桶中</li>
 *   <li>每条消息消费前需获取一个令牌</li>
 *   <li>桶满时多余的令牌被丢弃</li>
 *   <li>桶空时消费者等待直到有令牌可用</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ConsumerRateLimiter limiter = new ConsumerRateLimiter(100); // 每秒 100 条
 * // 在消费循环中
 * limiter.acquire(); // 阻塞直到获取令牌
 * processMessage(message);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class ConsumerRateLimiter {

    /**
     * 限流速率（每秒令牌数）
     */
    private final double permitsPerSecond;

    /**
     * 桶容量（最大令牌数）
     */
    private final double maxTokens;

    /**
     * 当前令牌数
     */
    private double tokens;

    /**
     * 上次补充令牌的时间戳（纳秒）
     */
    private long lastRefillTime;

    /**
     * 线程安全锁
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 是否启用限流
     */
    private final boolean enabled;

    /**
     * 创建限流器
     *
     * @param permitsPerSecond 每秒允许通过的令牌数，<= 0 表示不限流
     */
    public ConsumerRateLimiter(int permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            this.permitsPerSecond = 0;
            this.maxTokens = 0;
            this.tokens = 0;
            this.enabled = false;
        } else {
            this.permitsPerSecond = permitsPerSecond;
            // 桶容量 = 每秒速率，允许短时间内的突发
            this.maxTokens = permitsPerSecond;
            this.tokens = maxTokens;
            this.lastRefillTime = System.nanoTime();
            this.enabled = true;
        }
    }

    /**
     * 获取令牌，如果桶中没有令牌则阻塞等待
     *
     * <p>此方法会阻塞当前线程直到获取到一个令牌为止。
     * 如果限流器未启用（permitsPerSecond <= 0），则立即返回。
     *
     * <p><b>线程中断处理：</b>如果等待期间线程被中断，会设置中断标志并立即返回，
     * 不会抛出 IllegalMonitorStateException。
     */
    public void acquire() {
        if (!enabled) {
            return;
        }
        lock.lock();
        try {
            while (true) {
                refillTokens();
                if (tokens >= 1.0) {
                    tokens -= 1.0;
                    return;
                }
                // 计算需要等待的时间
                double deficit = 1.0 - tokens;
                long waitNanos = (long) (deficit / permitsPerSecond * TimeUnit.SECONDS.toNanos(1));
                if (waitNanos <= 0) {
                    waitNanos = 1;
                }
                // 释放锁后 sleep，避免持有锁阻塞其他线程
                lock.unlock();
                try {
                    Thread.sleep(TimeUnit.NANOSECONDS.toMillis(waitNanos),
                            (int) (waitNanos % TimeUnit.MILLISECONDS.toNanos(1)));
                } catch (InterruptedException e) {
                    // sleep 被中断时，当前线程已不持有锁，直接设置中断标志并返回
                    // 不会进入 finally 块的 lock.unlock()（因为锁已在上面释放）
                    Thread.currentThread().interrupt();
                    // 重新获取锁以进入 finally 块，避免 finally 中 unlock 抛 IllegalMonitorStateException
                    lock.lock();
                    return;
                }
                // sleep 结束后重新获取锁继续循环
                lock.lock();
            }
        } finally {
            // 只有在持有锁时才释放，避免 IllegalMonitorStateException
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 尝试获取令牌，不阻塞
     *
     * @return true 表示获取成功，false 表示当前无可用令牌
     */
    public boolean tryAcquire() {
        if (!enabled) {
            return true;
        }
        lock.lock();
        try {
            refillTokens();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 补充令牌
     *
     * 根据当前时间与上次补充时间的差值，计算应该新增的令牌数。
     */
    private void refillTokens() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTime;
        double tokensToAdd = elapsedNanos * permitsPerSecond / TimeUnit.SECONDS.toNanos(1);
        if (tokensToAdd > 0) {
            tokens = Math.min(maxTokens, tokens + tokensToAdd);
            lastRefillTime = now;
        }
    }

    /**
     * 获取当前可用令牌数（仅用于监控）
     *
     * @return 可用令牌数
     */
    public double getAvailableTokens() {
        lock.lock();
        try {
            refillTokens();
            return tokens;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取配置的限流速率
     *
     * @return 每秒令牌数
     */
    public double getPermitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * 判断限流器是否启用
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
}
