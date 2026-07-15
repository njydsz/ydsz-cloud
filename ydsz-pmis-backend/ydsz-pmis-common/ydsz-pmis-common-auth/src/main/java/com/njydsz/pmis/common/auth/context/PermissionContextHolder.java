package com.njydsz.pmis.common.auth.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 权限上下文持有者，用于在同一次请求内缓存权限相关信息。
 *
 * <p><b>已废弃：</b>请使用 {@link AuthContext} 替代，后者已包含 tenantId 字段。
 * 本类保留仅为向后兼容，将在 2.0.0 版本移除。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public final class PermissionContextHolder {

    private static final ThreadLocal<String> TENANT_ID = new TransmittableThreadLocal<>();

    private PermissionContextHolder() {
    }

    /**
     * 获取当前请求的租户 ID。
     *
     * @return 租户 ID，未设置时返回 null
     */
    public static String getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 设置当前请求的租户 ID。
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 清理当前线程的上下文数据。
     *
     * <p>必须在请求结束时调用（通常由 Filter 或 Interceptor 负责），
     * 防止 ThreadLocal 内存泄漏。
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}
