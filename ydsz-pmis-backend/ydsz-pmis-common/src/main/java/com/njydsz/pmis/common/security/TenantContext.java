package com.njydsz.pmis.common.security;

/**
 * 租户上下文（ThreadLocal）
 *
 * <p>当前阶段为单租户部署，{@link #DEFAULT_TENANT_ID} 恒为 1。
 * 未来多租户化时，由网关或登录拦截器在请求开始时调用 {@link #setTenantId(Long)}，
 * 请求结束时调用 {@link #clear()} 释放 ThreadLocal。
 *
 * <p>由 {@code PmisTenantLineHandler} 读取，注入到 MyBatis-Plus 的
 * {@code TenantLineInnerInterceptor}，自动为所有 SQL 追加 {@code WHERE tenant_id = ?}。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public final class TenantContext {

    /** 默认租户 ID（单租户部署） */
    public static final long DEFAULT_TENANT_ID = 1L;

    private static final ThreadLocal<Long> TENANT_HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 获取当前租户 ID，未设置时返回默认值 1
     *
     * @return 租户 ID
     */
    public static Long getTenantId() {
        Long id = TENANT_HOLDER.get();
        return id != null ? id : DEFAULT_TENANT_ID;
    }

    /**
     * 设置当前租户 ID（由网关/登录拦截器调用）
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    /**
     * 清除 ThreadLocal，防止线程池复用导致租户串号
     */
    public static void clear() {
        TENANT_HOLDER.remove();
    }
}
