package com.njydsz.common.base.ratelimit;

import java.time.Duration;

/**
 * 限流器接口。
 *
 * <p>提供统一的限流检查能力，支持多种实现：
 * <ul>
 *   <li>{@code InMemoryRateLimiter} - 基于本地滑动窗口计数的实现</li>
 *   <li>{@code RedisRateLimiterAdapter} - 基于 Redis + Lua 的分布式实现（由 redis 模块提供）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RateLimiter {

    /**
     * 尝试获取许可。
     *
     * @param key    限流维度键
     * @param limit  时间窗口内允许的最大请求数
     * @param window 时间窗口长度
     * @return true=允许通过，false=限流拒绝
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
