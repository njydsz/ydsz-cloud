package com.njydsz.common.tenant.ratelimit;

import java.time.Duration;

import com.njydsz.common.redis.service.RedisRateLimiter;

import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 租户级限流门面。
 *
 * <p>将 {@code common-redis} 的 {@code RedisRateLimiter} 包装为按租户维度限流，
 * 自动在限流 Key 前添加租户前缀。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 检查租户 API 调用配额
 * if (!tenantRateLimiter.tryAcquireTokenBucket("api:invoke", 100, 10, Duration.ofSeconds(60))) {
 *     throw new RuntimeException("API 调用配额已用尽");
 * }
 *
 * // 检查租户存储配额（固定窗口）
 * if (!tenantRateLimiter.tryAcquireFixedWindow("storage:upload", 1000, Duration.ofHours(1))) {
 *     throw new RuntimeException("存储配额已用尽");
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TenantRateLimiter {

    private final RedisRateLimiter delegate;

    public TenantRateLimiter(RedisRateLimiter delegate) {
        this.delegate = delegate;
    }

    /**
     * 尝试获取令牌桶令牌（按租户维度限流）。
     *
     * @param ruleName 限流规则名称
     * @param rate     令牌生成速率（每秒令牌数）
     * @param capacity 桶容量
     * @return true=获取成功，false=被限流
     */
    public boolean tryAcquireTokenBucket(String ruleName, int rate, int capacity) {
        String tenantId = TenantContextHolder.getTenantId();
        String key = tenantId != null
                ? "tenant:" + tenantId + ":" + ruleName
                : ruleName;
        return delegate.tryAcquireTokenBucket(key, rate, capacity);
    }

    /**
     * 尝试获取令牌桶令牌（按租户维度限流，自定义周期）。
     *
     * @param ruleName 限流规则名称
     * @param rate     令牌生成速率
     * @param capacity 桶容量
     * @param period   补充周期
     * @return true=获取成功，false=被限流
     */
    public boolean tryAcquireTokenBucket(String ruleName, int rate, int capacity, Duration period) {
        String tenantId = TenantContextHolder.getTenantId();
        String key = tenantId != null
                ? "tenant:" + tenantId + ":" + ruleName
                : ruleName;
        return delegate.tryAcquireTokenBucket(key, rate, capacity, period);
    }

    /**
     * 尝试获取固定窗口限流（按租户维度）。
     *
     * @param ruleName   限流规则名称
     * @param limit      窗口内最大请求数
     * @param window     窗口大小
     * @return true=允许，false=被限流
     */
    public boolean tryAcquireFixedWindow(String ruleName, int limit, Duration window) {
        String tenantId = TenantContextHolder.getTenantId();
        String key = tenantId != null
                ? "tenant:" + tenantId + ":" + ruleName
                : ruleName;
        return delegate.tryAcquireFixedWindow(key, limit, window);
    }
}
