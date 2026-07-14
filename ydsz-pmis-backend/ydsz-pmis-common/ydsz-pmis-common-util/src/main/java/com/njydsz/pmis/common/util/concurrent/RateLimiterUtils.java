package com.njydsz.pmis.common.util.concurrent;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 限流工具类
 *
 * <p>提供基于令牌桶和信号量的两种限流策略，纯 JDK 实现，零第三方依赖。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>信号量限流：固定并发数控制（适用于并发连接数限制）</li>
 *   <li>令牌桶限流：固定速率控制（适用于 API 限流、QPS 控制）</li>
 *   <li>支持按 key 隔离限流器实例</li>
 *   <li>支持 tryAcquire（非阻塞）和 acquire（阻塞等待）两种获取模式</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 信号量限流：限制最大并发数为 10
 * RateLimiterUtils.SemaphoreLimiter limiter = RateLimiterUtils.createSemaphoreLimiter("api-key", 10);
 * if (limiter.tryAcquire()) {
 *     try {
 *         // 执行业务逻辑
 *     } finally {
 *         limiter.release();
 *     }
 * }
 *
 * // 令牌桶限流：限制每秒 100 个请求
 * RateLimiterUtils.TokenBucketLimiter bucketLimiter = RateLimiterUtils.createTokenBucketLimiter("user-123", 100, Duration.ofSeconds(1));
 * if (bucketLimiter.tryAcquire()) {
 *     // 处理请求
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public class RateLimiterUtils {

    private static final ConcurrentHashMap<String, SemaphoreLimiter> SEMAPHORE_REGISTRY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TokenBucketLimiter> TOKEN_BUCKET_REGISTRY = new ConcurrentHashMap<>();

    private RateLimiterUtils() {
        throw new UnsupportedOperationException("RateLimiterUtils is a utility class and cannot be instantiated");
    }

    // ==================== 信号量限流器 ====================

    /**
     * 创建或获取信号量限流器
     *
     * @param key      限流器标识（如 API key、用户 ID）
     * @param maxPermits 最大许可数
     * @return 信号量限流器实例
     */
    public static SemaphoreLimiter createSemaphoreLimiter(String key, int maxPermits) {
        return SEMAPHORE_REGISTRY.computeIfAbsent(key, k -> new SemaphoreLimiter(k, maxPermits));
    }

    /**
     * 信号量限流器
     *
     * <p>基于 {@link Semaphore} 实现，适用于控制并发访问数量。
     * 获取的许可必须显式释放，建议使用 try-finally 模式。
     */
    public static class SemaphoreLimiter {
        private final String key;
        private final Semaphore semaphore;

        SemaphoreLimiter(String key, int maxPermits) {
            this.key = key;
            this.semaphore = new Semaphore(maxPermits, true);
        }

        /**
         * 尝试获取许可（非阻塞）
         *
         * @return 获取成功返回 true，否则返回 false
         */
        public boolean tryAcquire() {
            return semaphore.tryAcquire();
        }

        /**
         * 尝试获取许可（带超时）
         *
         * @param timeout 超时时间
         * @param unit    时间单位
         * @return 获取成功返回 true，超时返回 false
         * @throws InterruptedException 等待过程中被中断
         */
        public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
            return semaphore.tryAcquire(timeout, unit);
        }

        /**
         * 获取许可（阻塞等待）
         *
         * @throws InterruptedException 等待过程中被中断
         */
        public void acquire() throws InterruptedException {
            semaphore.acquire();
        }

        /**
         * 释放许可
         */
        public void release() {
            semaphore.release();
        }

        /**
         * 获取当前可用许可数
         *
         * @return 可用许可数
         */
        public int availablePermits() {
            return semaphore.availablePermits();
        }

        /**
         * 获取限流器标识
         *
         * @return 标识 key
         */
        public String getKey() {
            return key;
        }
    }

    // ==================== 令牌桶限流器 ====================

    /**
     * 创建或获取令牌桶限流器
     *
     * @param key            限流器标识
     * @param maxTokens      桶容量（最大令牌数）
     * @param refillInterval 补充间隔（每隔此时间补充一个令牌）
     * @return 令牌桶限流器实例
     */
    public static TokenBucketLimiter createTokenBucketLimiter(String key, int maxTokens, Duration refillInterval) {
        return TOKEN_BUCKET_REGISTRY.computeIfAbsent(key, k -> new TokenBucketLimiter(k, maxTokens, refillInterval));
    }

    /**
     * 令牌桶限流器
     *
     * <p>基于时间窗口的令牌桶算法，适用于控制请求速率（QPS）。
     * 令牌按固定速率补充，请求消耗令牌，桶满则丢弃新令牌。
     */
    public static class TokenBucketLimiter {
        private final String key;
        private final int maxTokens;
        private final long refillIntervalNanos;
        private long availableTokens;
        private long lastRefillNanos;

        TokenBucketLimiter(String key, int maxTokens, Duration refillInterval) {
            this.key = key;
            this.maxTokens = maxTokens;
            this.refillIntervalNanos = refillInterval.toNanos();
            this.availableTokens = maxTokens;
            this.lastRefillNanos = System.nanoTime();
        }

        /**
         * 尝试获取一个令牌（非阻塞，线程安全）
         *
         * @return 获取成功返回 true，令牌不足返回 false
         */
        public synchronized boolean tryAcquire() {
            refill();
            if (availableTokens >= 1) {
                availableTokens--;
                return true;
            }
            return false;
        }

        /**
         * 尝试获取指定数量的令牌
         *
         * @param tokens 需要的令牌数
         * @return 获取成功返回 true，令牌不足返回 false
         */
        public synchronized boolean tryAcquire(int tokens) {
            if (tokens <= 0 || tokens > maxTokens) {
                throw new IllegalArgumentException("tokens must be between 1 and " + maxTokens);
            }
            refill();
            if (availableTokens >= tokens) {
                availableTokens -= tokens;
                return true;
            }
            return false;
        }

        /**
         * 补充令牌（基于时间间隔）
         */
        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed >= refillIntervalNanos) {
                long newTokens = elapsed / refillIntervalNanos;
                availableTokens = Math.min(maxTokens, availableTokens + newTokens);
                lastRefillNanos += newTokens * refillIntervalNanos;
            }
        }

        /**
         * 获取当前可用令牌数
         *
         * @return 可用令牌数
         */
        public synchronized long getAvailableTokens() {
            refill();
            return availableTokens;
        }

        /**
         * 获取限流器标识
         *
         * @return 标识 key
         */
        public String getKey() {
            return key;
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 移除指定 key 的信号量限流器
     *
     * @param key 限流器标识
     */
    public static void removeSemaphoreLimiter(String key) {
        SEMAPHORE_REGISTRY.remove(key);
    }

    /**
     * 移除指定 key 的令牌桶限流器
     *
     * @param key 限流器标识
     */
    public static void removeTokenBucketLimiter(String key) {
        TOKEN_BUCKET_REGISTRY.remove(key);
    }

    /**
     * 清空所有限流器
     */
    public static void clearAll() {
        SEMAPHORE_REGISTRY.clear();
        TOKEN_BUCKET_REGISTRY.clear();
    }
}
