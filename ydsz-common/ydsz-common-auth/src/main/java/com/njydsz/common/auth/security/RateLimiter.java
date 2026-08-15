package com.njydsz.common.auth.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.redis.service.RedisRateLimiter;

/**
 * 基于固定窗口的限流器（支持 Redis 分布式降级到本地内存）
 *
 * <p>用于限制权限校验和 Token 验证的调用频率，防止被盗 Token 高频发起请求。
 *
 * <p><b>算法：</b>固定时间窗口计数器，每个窗口期内允许最多 maxRequests 次请求。
 *
 * <p><b>P0-1 架构优化：</b>当 {@link RedisRateLimiter} 可用时委托其固定窗口限流实现
 * （分布式一致）；RedisRateLimiter 不可用时降级为本地内存实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RedisRateLimiter
 * @deprecated 自 v2.0.0 起，统一使用 {@link com.njydsz.common.tenant.ratelimit.TenantRateLimiter}
 *             作为租户级限流门面。请使用 TenantRateLimiter 替代本类的功能。
 *             参考：<a href="https://github.com/njydsz/ydsz-cloud/docs/ratelimit-migration-guide.md">限流迁移指南</a>
 */
@Deprecated
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final RedisRateLimiter redisRateLimiter;

    /**
     * 构建 Rate Limiter（带 Redis 分布式能力）。
     *
     * @param maxRequests     窗口期内最大请求数
     * @param windowDuration  窗口时长
     * @param unit            时间单位
     * @param redisRateLimiter Redis 限流器（可选，不可用时降级到本地内存）
     */
    public RateLimiter(int maxRequests, long windowDuration, TimeUnit unit,
                       RedisRateLimiter redisRateLimiter) {
        this.maxRequests = maxRequests;
        this.windowMillis = unit.toMillis(windowDuration);
        this.redisRateLimiter = redisRateLimiter;
        if (redisRateLimiter != null) {
            log.info("[AuthRateLimiter] 启用 Redis 分布式限流 | maxRequests={} | window={}ms",
                    maxRequests, windowMillis);
        } else {
            log.info("[AuthRateLimiter] 启用本地内存限流 | maxRequests={} | window={}ms",
                    maxRequests, windowMillis);
        }
    }

    /**
     * 尝试获取许可。
     *
     * @param key 限流键（如 userId、IP 等）
     * @return 允许请求返回 true，超过限流阈值返回 false
     */
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }

        if (redisRateLimiter != null) {
            return tryAcquireDistributed(key);
        }
        return tryAcquireLocal(key);
    }

    /**
     * 获取指定 key 的当前窗口请求计数。
     *
     * @param key 限流键
     * @return 当前请求数
     */
    public int getCurrentCount(String key) {
        Window window = windows.get(key);
        return window != null ? window.counter.get() : 0;
    }

    /**
     * 清理过期的窗口数据。
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= windowMillis);
    }

    /**
     * 获取最大请求数。
     *
     * @return 最大请求数
     */
    public int getMaxRequests() {
        return maxRequests;
    }

    /**
     * 获取窗口时长（毫秒）。
     *
     * @return 窗口时长
     */
    public long getWindowMillis() {
        return windowMillis;
    }

    // ==================== 私有方法 ====================

    /**
     * 分布式限流（基于 Redis 固定窗口）
     */
    private boolean tryAcquireDistributed(String key) {
        try {
            boolean acquired = redisRateLimiter.tryAcquireFixedWindow(
                    "auth:" + key, maxRequests, Duration.ofMillis(windowMillis));
            if (!acquired) {
                log.warn("[AuthRateLimiter] 分布式限流触发 | key={} | max={} | window={}ms",
                        key, maxRequests, windowMillis);
            }
            return acquired;
        } catch (Exception e) {
            log.warn("[AuthRateLimiter] Redis 限流异常，降级本地 | key={} | error={}", key, e.getMessage());
            return tryAcquireLocal(key);
        }
    }

    /**
     * 本地内存限流（固定窗口计数器）
     */
    private boolean tryAcquireLocal(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart >= windowMillis) {
                return new Window(now);
            }
            return existing;
        });

        int count = window.counter.incrementAndGet();
        if (count > maxRequests) {
            if (count == maxRequests + 1) {
                log.warn("[AuthRateLimiter] 本地限流触发 | key={} | max={} | window={}ms",
                        key, maxRequests, windowMillis);
            }
            return false;
        }
        return true;
    }

    /**
     * 时间窗口数据载体。
     */
    private static class Window {
        final long windowStart;
        final AtomicInteger counter = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
