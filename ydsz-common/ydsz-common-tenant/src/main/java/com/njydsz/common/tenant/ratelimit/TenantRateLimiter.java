package com.njydsz.common.tenant.ratelimit;

import java.time.Duration;

import com.njydsz.common.jdbc.exception.TenantIsolationException;
import com.njydsz.common.redis.service.RedisRateLimiter;

import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 租户级限流门面。
 *
 * <p>将 {@code ydsz-common-redis} 的 {@code RedisRateLimiter} 包装为按租户维度限流，
 * 自动在限流 Key 前添加租户前缀，确保各租户限流配额相互隔离。
 *
 * <p>支持的算法：
 * <ul>
 *   <li><b>固定窗口</b>：按时间窗口计数，窗口切换时存在 2x 突发，适用于粗粒度限流</li>
 *   <li><b>滑动窗口</b>：分桶计数，限流平滑，适用于严格限流场景</li>
 *   <li><b>令牌桶</b>：支持突发流量，按速率持续补充令牌，适用于流量整形</li>
 * </ul>
 *
 * <p>所有限流方法均返回 boolean，调用方根据返回值决定是否抛出异常。
 * 建议统一使用 {@link TenantIsolationException} 表示租户级访问受限，
 * 与模块异常体系保持一致。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 检查租户 API 调用配额（令牌桶）
 * if (!tenantRateLimiter.tryAcquireTokenBucket("api:invoke", 100, 10)) {
 *     throw new TenantIsolationException("租户 API 调用配额已用尽，请稍后重试");
 * }
 *
 * // 检查租户存储配额（固定窗口）
 * if (!tenantRateLimiter.tryAcquireFixedWindow("storage:upload", 1000, Duration.ofHours(1))) {
 *     throw new TenantIsolationException("租户存储配额已用尽，请升级套餐");
 * }
 *
 * // 检查租户登录频率（滑动窗口）
 * if (!tenantRateLimiter.tryAcquireSlidingWindow("user:login", 5, Duration.ofMinutes(1))) {
 *     throw new TenantIsolationException("登录频率过高，请稍后重试");
 * }
 * }</pre>
 *
 * <p><b>注意：</b>本门面是租户级限流的唯一推荐入口，各模块应避免自行实现限流逻辑，
 * 以确保限流配额按租户维度统一管理与监控。
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
     * <p>使用默认周期（1 秒），请求 1 个令牌。
     *
     * @param ruleName 限流规则名称
     * @param rate     令牌生成速率（每秒令牌数）
     * @param capacity 桶容量
     * @return true=获取成功，false=被限流
     */
    public boolean tryAcquireTokenBucket(String ruleName, int rate, int capacity) {
        return tryAcquireTokenBucket(ruleName, rate, capacity, Duration.ofSeconds(1), 1);
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
        return tryAcquireTokenBucket(ruleName, rate, capacity, period, 1);
    }

    /**
     * 尝试获取令牌桶令牌（按租户维度限流，完全自定义）。
     *
     * @param ruleName 限流规则名称
     * @param rate     令牌生成速率
     * @param capacity 桶容量
     * @param period   补充周期
     * @param permits  本次请求消耗的令牌数
     * @return true=获取成功，false=被限流
     */
    public boolean tryAcquireTokenBucket(String ruleName, int rate, int capacity, Duration period, int permits) {
        String key = buildKey(ruleName);
        return delegate.tryAcquireTokenBucket(key, rate, capacity, period, permits);
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
        String key = buildKey(ruleName);
        return delegate.tryAcquireFixedWindow(key, limit, window);
    }

    /**
     * 尝试获取滑动窗口限流（按租户维度）。
     *
     * <p>使用分桶计数法，内存占用恒定，限流平滑。
     *
     * @param ruleName   限流规则名称
     * @param limit      窗口内最大请求数
     * @param window     窗口大小
     * @return true=允许，false=被限流
     */
    public boolean tryAcquireSlidingWindow(String ruleName, int limit, Duration window) {
        String key = buildKey(ruleName);
        return delegate.tryAcquireSlidingWindow(key, limit, window);
    }

    /**
     * 重置限流状态（按租户维度）。
     *
     * @param ruleName 限流规则名称
     */
    public void reset(String ruleName) {
        String key = buildKey(ruleName);
        delegate.reset(key);
    }

    /**
     * 查询限流键的剩余生存时间（秒）。
     *
     * <p>用于诊断限流状态，例如查询冷却剩余时间。
     *
     * @param ruleName 限流规则名称
     * @return 剩余秒数（0=可操作，>0=冷却中，<0=键不存在或查询失败）
     */
    public long getRemainingSeconds(String ruleName) {
        String key = buildKey(ruleName);
        return delegate.getRemainingSeconds(key);
    }

    /**
     * 构建租户隔离的限流 Key。
     *
     * <p>格式：Tenant:{tenantId}:{ruleName}，当租户 ID 为空时回退到原始规则名。
     *
     * @param ruleName 限流规则名称
     * @return 带租户前缀的限流 Key
     */
    private String buildKey(String ruleName) {
        String tenantId = TenantContextHolder.getTenantId();
        return tenantId != null
                ? "tenant:" + tenantId + ":" + ruleName
                : ruleName;
    }
}
