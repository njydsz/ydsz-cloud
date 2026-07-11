package com.njydsz.pmis.common.config;

/**
 * 租户上下文持有器 —— 线程本地存储当前租户 ID。
 * <p>
 * security 模块在认证后通过此 holder 设置租户 ID，
 * data 模块的 {@link PmisTenantLineHandler} 从此 holder 读取。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public final class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    /**
     * 设置当前租户 ID。
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    /**
     * 获取当前租户 ID。
     *
     * @return 租户 ID，未设置返回 null
     */
    public static Long getTenantId() {
        return TENANT_HOLDER.get();
    }

    /**
     * 清除当前线程的租户 ID。
     */
    public static void clear() {
        TENANT_HOLDER.remove();
    }
}
