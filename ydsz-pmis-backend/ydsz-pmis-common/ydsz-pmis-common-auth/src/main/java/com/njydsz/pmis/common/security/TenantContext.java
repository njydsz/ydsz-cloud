package com.njydsz.pmis.common.security;

/**
 * 租户上下文
 *
 * <p>基于 ThreadLocal 的租户 ID 透传工具，供业务层在非 Web 线程（异步任务、定时任务、
 * 消息消费）中获取当前租户标识。Web 请求线程由网关 / 过滤器在请求头解析后写入。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 写入
 * TenantContext.set("1");
 * // 读取
 * String tenantId = TenantContext.getTenantId();
 * // 清理（线程复用前必须调用，防止租户串号）
 * TenantContext.clear();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class TenantContext {

    /** 默认租户 ID（未登录或无租户上下文时使用） */
    public static final String DEFAULT_TENANT_ID = "1";

    private static final ThreadLocal<String> TENANT_HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 设置当前线程的租户 ID
     *
     * @param tenantId 租户 ID（可为 null）
     */
    public static void set(String tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    /**
     * 获取当前线程的租户 ID
     *
     * @return 租户 ID；未设置时返回 {@link #DEFAULT_TENANT_ID}
     */
    public static String get() {
        String tenantId = TENANT_HOLDER.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    /**
     * 获取当前线程的租户 ID（语义别名，等同 {@link #get()}）
     *
     * @return 租户 ID；未设置时返回 {@link #DEFAULT_TENANT_ID}
     */
    public static String getTenantId() {
        return get();
    }

    /**
     * 清除当前线程的租户 ID
     */
    public static void clear() {
        TENANT_HOLDER.remove();
    }
}
