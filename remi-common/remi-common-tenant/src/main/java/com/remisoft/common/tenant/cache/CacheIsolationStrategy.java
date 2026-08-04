package com.remisoft.common.tenant.cache;

import com.remisoft.common.tenant.TenantContext;
import com.remisoft.common.tenant.TenantContextHolder;

/**
 * 租户缓存隔离策略枚举。
 *
 * <p>定义不同的 Redis Key 隔离方案：
 * <ul>
 *   <li>{@link #KEY_PREFIX} — Key 前缀方案（默认）：{@code {tenantId}:{originalKey}}</li>
 *   <li>{@link #REDIS_DB} — Redis DB 切换方案：每租户使用独立 Redis DB（SELECT 0-15）</li>
 *   <li>{@link #NONE} — 不隔离：所有租户共享缓存</li>
 * </ul>
 *
 * <p>通过配置 {@code remi.tenant.cache-isolation-strategy} 选择策略。
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum CacheIsolationStrategy {

    /**
     * Key 前缀方案（默认推荐）。
     *
     * <p>格式：{@code {tenantId}:{originalKey}}
     * <p>优点：简单、无连接数限制、跨租户查询方便
     * <p>缺点：所有租户共享同一 Redis 实例，Key 数量膨胀
     */
    KEY_PREFIX,

    /**
     * Redis DB 切换方案。
     *
     * <p>每租户使用独立 Redis DB（SELECT 0-15）。
     * <p>优点：物理隔离、性能好
     * <p>缺点：最多 16 个租户、Redis 连接数多
     */
    REDIS_DB,

    /**
     * 不隔离。
     *
     * <p>所有租户共享缓存，适用于单租户模式或系统级缓存。
     */
    NONE;

    /**
     * 根据策略解析最终 Redis Key。
     *
     * @param originalKey 原始 Key
     * @param strategy   隔离策略
     * @return 处理后的 Key
     */
    public static String resolveKey(String originalKey, CacheIsolationStrategy strategy) {
        if (strategy == null || strategy == NONE) {
            return originalKey;
        }
        if (strategy == KEY_PREFIX) {
            TenantContext context = TenantContextHolder.get();
            if (context == null || context.isSkipIsolation()
                    || context.isSuperAdmin() || context.getTenantId() == null) {
                return originalKey;
            }
            return context.getTenantId() + ":" + originalKey;
        }
        // REDIS_DB 策略：Key 不变，由 RedisConnectionFactory 层面切换 DB
        return originalKey;
    }
}
