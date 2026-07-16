package com.njydsz.common.safe.ratelimit;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地限流器（Redis 降级方案）
 *
 * <p>当 Redis 不可用时，降级使用本地 {@link Semaphore} + 时间窗口实现限流。
 * 相比直接放行（fail-open），本地限流仍能提供基本的保护能力。
 *
 * <p><b>实现原理：</b>
 * 使用 Semaphore 控制并发请求数，配合时间窗口定期释放许可。
 * 虽然不如 Redis 滑动窗口精确，但在 Redis 不可用时提供基本的过载保护。
 *
 * @since 1.0.0
 */
public class LocalRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LocalRateLimiter.class);

    private final int maxPermits;
    private final Semaphore semaphore;
    private final long refillIntervalMillis;
    private volatile long lastRefillTime;

    /**
     * @param maxPermits          最大许可数（窗口内允许的请求数）
     * @param refillIntervalSeconds 补充间隔（秒，等于滑动窗口大小）
     */
    public LocalRateLimiter(int maxPermits, int refillIntervalSeconds) {
        this.maxPermits = maxPermits;
        this.semaphore = new Semaphore(maxPermits, true);
        this.refillIntervalMillis = refillIntervalSeconds * 1000L;
        this.lastRefillTime = System.currentTimeMillis();
    }

    /**
     * 尝试获取许可
     *
     * @return true 获取成功（放行），false 获取失败（限流）
     */
    public boolean tryAcquire() {
        refillIfNeeded();
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            log.debug("【本地限流】许可不足，请求被限流 | available={}", semaphore.availablePermits());
        }
        return acquired;
    }

    /**
     * 定期补充许可
     *
     * <p>每次调用时检查是否到了补充时间窗口，到了则将许可数补充到最大值。
     * 使用 CAS 风格的 compareAndSet 确保只有一个线程执行补充。
     */
    private void refillIfNeeded() {
        long now = System.currentTimeMillis();
        long lastTime = lastRefillTime;
        if (now - lastTime >= refillIntervalMillis) {
            if (compareAndSetLastRefillTime(lastTime, now)) {
                int current = semaphore.availablePermits();
                int toRelease = maxPermits - current;
                if (toRelease > 0) {
                    semaphore.release(toRelease);
                }
            }
        }
    }

    /**
     * CAS 风格的 lastRefillTime 更新（简化版，利用 volatile 可见性）
     */
    private synchronized boolean compareAndSetLastRefillTime(long expected, long update) {
        if (lastRefillTime == expected) {
            lastRefillTime = update;
            return true;
        }
        return false;
    }

    /**
     * 获取当前可用许可数
     *
     * @return 可用许可数
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
