package com.remisoft.common.tenant.redis;

import com.remisoft.common.core.context.RequestContext;
import com.remisoft.common.tenant.TenantContext;

/**
 * 租户感知的 Redis Key 构建器。
 *
 * <p>所有 Redis Key 必须通过此类构建，自动添加 {@code {tenantId}:} 前缀。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 不带前缀
 * String key = "user:001";
 *
 * // 带租户前缀（多租户模式下）
 * String redisKey = TenantAwareRedisKey.resolve(key);
 * // 结果：tenant_001:user:001
 *
 * // 无租户上下文时不加前缀
 * TenantAwareRedisKey.resolve(key);  // 结果：user:001
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class TenantAwareRedisKey {

    private static final String PREFIX_TEMPLATE = "{tenantId}:";

    private TenantAwareRedisKey() {
    }

    /**
     * 构建带租户前缀的 Redis Key。
     *
     * <p>无租户上下文、跳过隔离、或超级管理员时不加前缀。
     *
     * @param key 原始 Key
     * @return 带前缀的 Key 或原始 Key
     */
    public static String resolve(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        TenantContext context = (TenantContext) RequestContext.getTenantContext();
        if (context == null || context.isSkipIsolation()
                || context.isSuperAdmin() || context.getTenantId() == null) {
            return key;
        }
        return PREFIX_TEMPLATE.replace("{tenantId}", context.getTenantId()) + key;
    }
}
