package com.njydsz.pmis.common.auth.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 权限上下文持有者，用于在同一次请求内缓存权限相关信息。
 *
 * <p>通过 {@link TransmittableThreadLocal} 避免同一次请求内多次 Redis 查询，
 * 主要用于缓存 tenantId 等从用户信息中解析的数据。
 *
 * <p><b>线程安全：</b>
 * 使用 {@link TransmittableThreadLocal}（TTL）确保线程隔离并安全支持线程池场景下的上下文透传，
 * 请求结束后必须调用 {@link #clear()} 清理。
 * 相比原生 {@link ThreadLocal}，TTL 解决了线程池复用时上下文泄露的问题。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
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
