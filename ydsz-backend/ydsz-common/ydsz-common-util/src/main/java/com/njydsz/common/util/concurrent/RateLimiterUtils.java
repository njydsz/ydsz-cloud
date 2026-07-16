package com.njydsz.common.util.concurrent;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
 *   <li>注册表自动淘汰：超过阈值时自动清理空闲限流器</li>
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
 * @author ydsz-team
 * @since 1.1.0
 */
public class RateLimiterUtils {

    /** 注册表最大容量，超过后触发空闲清理 */
    private static final int MAX_REGISTRY_SIZE = 10_000;

    /** 默认空闲超时时间（毫秒），超过此时间未访问的限流器将被清理 */
    private static final long DEFAULT_MAX_IDLE_MILLIS = 30 * 60 * 1000L;

    /** 自动清理调度间隔（毫秒），默认 5 分钟 */
    private static final long AUTO_CLEANUP_INTERVAL_MILLIS = 5 * 60 * 1000L;

    private static final ConcurrentHashMap<String, SemaphoreLimiter> SEMAPHORE_REGISTRY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TokenBucketLimiter> TOKEN_BUCKET_REGISTRY = new ConcurrentHashMap<>();

    /** 自动清理调度器（daemon 线程，懒加载） */
    private static volatile ScheduledExecutorService cleanupScheduler;

    private RateLimiterUtils() {
        throw new UnsupportedOperationException("RateLimiterUtils is a utility class and cannot be instantiated");
    }

    /**
     * 启动自动清理调度器
     *
     * <p>使用 daemon 线程定期执行 {@link #cleanupStale()} 清理空闲限流器。
     * 调度器为懒加载，首次调用 {@link #createSemaphoreLimiter} 或 {@link #createTokenBucketLimiter} 时自动启动。
     * 也可通过此方法手动启动，支持自定义清理间隔。
     *
     * @param intervalMillis 清理间隔（毫秒）
     * @param maxIdleMillis  最大空闲时间（毫秒）
     */
    public static synchronized void startAutoCleanup(long intervalMillis, long maxIdleMillis) {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            return;
        }
        cleanupScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "ratelimiter-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupStale(maxIdleMillis);
            } catch (Exception e) {
                // 清理异常不影响调度器运行
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 使用默认参数启动自动清理调度器
     *
     * <p>清理间隔 5 分钟，空闲超时 30 分钟。
     */
    public static void startAutoCleanup() {
        startAutoCleanup(AUTO_CLEANUP_INTERVAL_MILLIS, DEFAULT_MAX_IDLE_MILLIS);
    }

    /**
     * 停止自动清理调度器
     *
     * <p>应用关闭时调用，执行优雅关闭。
     */
    public static synchronized void stopAutoCleanup() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 确保自动清理调度器已启动（懒加载）
     */
    private static void ensureAutoCleanupStarted() {
        if (cleanupScheduler == null || cleanupScheduler.isShutdown()) {
            synchronized (RateLimiterUtils.class) {
                if (cleanupScheduler == null || cleanupScheduler.isShutdown()) {
                    startAutoCleanup(AUTO_CLEANUP_INTERVAL_MILLIS, DEFAULT_MAX_IDLE_MILLIS);
                }
            }
        }
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
        ensureAutoCleanupStarted();
        evictIfNeeded();
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
        private final AtomicLong lastAccessTime;

        SemaphoreLimiter(String key, int maxPermits) {
            this.key = key;
            this.semaphore = new Semaphore(maxPermits, true);
            this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
        }

        /**
         * 尝试获取许可（非阻塞）
         *
         * @return 获取成功返回 true，否则返回 false
         */
        public boolean tryAcquire() {
            touch();
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
            touch();
            return semaphore.tryAcquire(timeout, unit);
        }

        /**
         * 获取许可（阻塞等待）
         *
         * @throws InterruptedException 等待过程中被中断
         */
        public void acquire() throws InterruptedException {
            touch();
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

        /**
         * 获取最后访问时间
         *
         * @return 最后访问时间戳（毫秒）
         */
        public long getLastAccessTime() {
            return lastAccessTime.get();
        }

        private void touch() {
            lastAccessTime.set(System.currentTimeMillis());
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
        ensureAutoCleanupStarted();
        evictIfNeeded();
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
        private final AtomicLong lastAccessTime;

        TokenBucketLimiter(String key, int maxTokens, Duration refillInterval) {
            this.key = key;
            this.maxTokens = maxTokens;
            this.refillIntervalNanos = refillInterval.toNanos();
            this.availableTokens = maxTokens;
            this.lastRefillNanos = System.nanoTime();
            this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
        }

        /**
         * 尝试获取一个令牌（非阻塞，线程安全）
         *
         * @return 获取成功返回 true，令牌不足返回 false
         */
        public synchronized boolean tryAcquire() {
            touch();
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
            touch();
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

        /**
         * 获取最后访问时间
         *
         * @return 最后访问时间戳（毫秒）
         */
        public long getLastAccessTime() {
            return lastAccessTime.get();
        }

        private void touch() {
            lastAccessTime.set(System.currentTimeMillis());
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

    /**
     * 清理空闲超过指定时间的限流器
     *
     * <p>建议在定时任务中调用此方法，防止 per-user/per-key 场景下的注册表无限增长。
     *
     * @param maxIdleMillis 最大空闲时间（毫秒），超过此时间未访问的限流器将被移除
     * @return 清理的限流器总数
     */
    public static int cleanupStale(long maxIdleMillis) {
        long now = System.currentTimeMillis();
        int semaphoreRemoved = cleanupStaleMap(SEMAPHORE_REGISTRY, now, maxIdleMillis);
        int tokenBucketRemoved = cleanupStaleMap(TOKEN_BUCKET_REGISTRY, now, maxIdleMillis);
        return semaphoreRemoved + tokenBucketRemoved;
    }

    /**
     * 使用默认空闲超时（30 分钟）清理空闲限流器
     *
     * @return 清理的限流器总数
     */
    public static int cleanupStale() {
        return cleanupStale(DEFAULT_MAX_IDLE_MILLIS);
    }

    /**
     * 获取信号量限流器注册表大小
     *
     * @return 注册表大小
     */
    public static int getSemaphoreRegistrySize() {
        return SEMAPHORE_REGISTRY.size();
    }

    /**
     * 获取令牌桶限流器注册表大小
     *
     * @return 注册表大小
     */
    public static int getTokenBucketRegistrySize() {
        return TOKEN_BUCKET_REGISTRY.size();
    }

    /**
     * 当注册表大小超过阈值时自动清理空闲限流器
     */
    private static void evictIfNeeded() {
        int totalSize = SEMAPHORE_REGISTRY.size() + TOKEN_BUCKET_REGISTRY.size();
        if (totalSize >= MAX_REGISTRY_SIZE) {
            cleanupStale(DEFAULT_MAX_IDLE_MILLIS);
        }
    }

    /**
     * 清理单个注册表中的空闲条目
     */
    private static <V> int cleanupStaleMap(ConcurrentHashMap<String, V> map, long now, long maxIdleMillis) {
        int removed = 0;
        var iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            V value = entry.getValue();
            long lastAccess = getLastAccessTime(value);
            if (now - lastAccess > maxIdleMillis) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * 反射获取限流器的最后访问时间
     */
    private static long getLastAccessTime(Object limiter) {
        if (limiter instanceof SemaphoreLimiter sl) {
            return sl.getLastAccessTime();
        }
        if (limiter instanceof TokenBucketLimiter tbl) {
            return tbl.getLastAccessTime();
        }
        return System.currentTimeMillis();
    }
}
