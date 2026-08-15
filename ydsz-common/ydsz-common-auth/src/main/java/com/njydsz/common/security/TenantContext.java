package com.njydsz.common.security;

import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.core.context.RequestContext;

/**
 * 租户上下文
 *
 * <p>基于 RequestContext 的租户 ID 透传工具，供业务层在非 Web 线程（异步任务、定时任务、
 * 消息消费）中获取当前租户标识。Web 请求线程由网关 / 过滤器在请求头解析后写入。
 *
 * <p><b>设计说明：</b>本类委托给 {@link RequestContext} 存储租户 ID，消除双源问题。
 * 所有租户 ID 的读写统一通过 RequestContext 的 TransmittableThreadLocal 管理，
 * 确保在异步任务、线程池等场景下自动传播。
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
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TenantContext {

    /** 默认租户 ID（未登录或无租户上下文时使用，委托 {@link SystemConstants#DEFAULT_TENANT_ID}） */
    public static final String DEFAULT_TENANT_ID = SystemConstants.DEFAULT_TENANT_ID;

    private TenantContext() {
    }

    /**
     * 设置当前线程的租户 ID
     *
     * @param tenantId 租户 ID（可为 null）
     */
    public static void set(String tenantId) {
        RequestContext.setTenantId(tenantId);
    }

    /**
     * 获取当前线程的租户 ID
     *
     * @return 租户 ID；未设置时返回 {@link #DEFAULT_TENANT_ID}
     */
    public static String get() {
        String tenantId = RequestContext.getTenantId();
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
        RequestContext.setTenantId(null);
    }
}
