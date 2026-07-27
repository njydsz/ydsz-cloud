package com.njydsz.common.tenant.ratelimit;

import com.njydsz.common.tenant.TenantContextHolder;

import com.njydsz.common.redis.service.RedisRateLimiter;
/**
 * 租户级限流门面。
 *
 * <p>将 {@code common-redis} 的 {@code RedisRateLimiter} 包装为按租户维度限流，
 * 自动在限流 Key 前添加租户前缀。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 检查租户 API 调用配额
 * if (!tenantRateLimiter.tryAcquire("api:invoke", 100, 60)) {
 *     throw new TenantRateLimitExceededException("API 调用配额已用尽");
 * }
 *
 * // 检查租户存储配额
 * if (!tenantRateLimiter.tryAcquire("storage:upload", 1000, 3600)) {
 *     throw new TenantRateLimitExceededException("存储配额已用尽");
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
     * @param ruleName     限流规则名称
     * @param capacity     桶容量
     * @param refillSeconds 补充间隔（秒）
     * @return true=获取成功，false=被限流
     */
    public boolean tryAcquire(String ruleName, int capacity, int refillSeconds) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            // 无租户上下文 → 全局限流
            return delegate.tryAcquireTokenBucket(ruleName, capacity, refillSeconds);
        }
        String tenantKey = "tenant:" + tenantId + ":" + ruleName;
        return delegate.tryAcquireTokenBucket(tenantKey, capacity, refillSeconds);
    }

    /**
     * 尝试获取固定窗口限流（按租户维度）。
     *
     * @param ruleName   限流规则名称
     * @param maxCount   窗口内最大请求数
     * @param windowSeconds 窗口大小（秒）
     * @return true=允许，false=被限流
     */
    public boolean tryAcquireFixedWindow(String ruleName, int maxCount, int windowSeconds) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return delegate.tryAcquireFixedWindow(ruleName, maxCount, windowSeconds);
        }
        String tenantKey = "tenant:" + tenantId + ":" + ruleName;
        return delegate.tryAcquireFixedWindow(tenantKey, maxCount, windowSeconds);
    }
}
