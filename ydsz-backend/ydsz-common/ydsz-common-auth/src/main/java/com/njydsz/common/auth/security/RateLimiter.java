package com.njydsz.common.auth.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于滑动窗口的内存级 Rate Limiter。
 *
 * <p>用于限制权限校验和 Token 验证的调用频率，防止被盗 Token 高频发起请求。
 *
 * <p><b>算法：</b>固定时间窗口计数器，每个窗口期内允许最多 maxRequests 次请求。
 *
 * <p><b>限制：</b>此实现为单机内存级别，不支持分布式限流。
 * 生产环境如需分布式限流，请集成 Redis + Lua 脚本方案
 * （参见 {@code com.njydsz.common.redis.service.RedisRateLimiter}）。
 *
 * <p><b>设计说明：</b>本类为 Auth 模块内部的轻量级限流器，
 * 用于在 Redis 不可用时提供降级保护。与 {@code safe} 模块的
 * {@code RateLimitFilter} 和 {@code redis} 模块的 {@code RedisRateLimiter}
 * 互为补充，不构成重复设计。
 *
 * @since 1.1.0

 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 构建 Rate Limiter。
     *
     * @param maxRequests    窗口期内最大请求数
     * @param windowDuration 窗口时长
     * @param unit           时间单位
     */
    public RateLimiter(int maxRequests, long windowDuration, TimeUnit unit) {
        this.maxRequests = maxRequests;
        this.windowMillis = unit.toMillis(windowDuration);
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
                log.warn("Rate limit exceeded: key={}, max={}, window={}ms",
                        key, maxRequests, windowMillis);
            }
            return false;
        }
        return true;
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
