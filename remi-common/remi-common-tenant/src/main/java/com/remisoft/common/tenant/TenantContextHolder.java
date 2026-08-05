package com.remisoft.common.tenant;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.List;
import java.util.Collections;

import com.remisoft.common.core.context.RequestContext;
import com.remisoft.common.core.constant.SystemConstants;
/**
 * 租户上下文统一持有者。
 *
 * <p>基于 {@link TransmittableThreadLocal}，支持线程池场景自动传播。
 * <p>全项目唯一租户上下文入口，替代 {@code RequestContext.getTenantId()}
 * 和 {@code AuthInfoUtils.getTenantId()} 双路径获取。
 *
 * <p><b>v1.1.0 变更：</b>与 {@link RequestContext} 双向同步，
 * {@code set()} 时同步写入 RequestContext，{@code getTenantId()} 时
 * 回退查询 RequestContext，确保跨模块的租户一致性。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 设置上下文
 * TenantContextHolder.set(TenantContext.of("tenant_001"));
 *
 * // 获取租户 ID
 * String tenantId = TenantContextHolder.getTenantId();
 *
 * // 异步传播
 * TenantContext snapshot = TenantContextHolder.snapshot();
 * executor.submit(() -> {
 *     TenantContextHolder.restore(snapshot);
 *     try {
 *         // 业务逻辑
 *     } finally {
 *         TenantContextHolder.clear();
 *     }
 * });
 *
 * // 清除
 * TenantContextHolder.clear();
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @deprecated v1.9.0 起弃用，由 {@link com.remisoft.common.core.context.RequestContext} 统一替代
 */
@Deprecated
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> HOLDER = new TransmittableThreadLocal<>();

    private TenantContextHolder() {
    }

    /**
     * 设置当前线程的租户上下文。
     *
     * <p>同时将租户 ID 同步写入 {@link RequestContext}，确保
     * 其他模块通过 RequestContext 也能获取到正确的租户 ID。
     *
     * @param context 租户上下文，null 等同于 clear
     */
    public static void set(TenantContext context) {
        if (context == null) {
            clear();
            return;
        }
        HOLDER.set(context);
        // 双向同步：写入 RequestContext，确保跨模块一致性
        if (context.getTenantId() != null) {
            RequestContext.setTenantId(context.getTenantId());
        }
    }

    /**
     * 获取当前线程的租户上下文。
     *
     * @return 租户上下文，未设置返回 null
     */
    public static TenantContext get() {
        return HOLDER.get();
    }

    /**
     * 获取当前租户 ID（便捷方法）。
     *
     * <p>优先从本地上下文获取；如果本地上下文未设置，则回退到
     * {@link RequestContext#getTenantId()}，确保跨模块一致性。
     *
     * @return 租户 ID；均未设置时回退返回 {@link SystemConstants#DEFAULT_TENANT_ID}
     */
    public static String getTenantId() {
        TenantContext context = HOLDER.get();
        if (context != null && context.getTenantId() != null) {
            return context.getTenantId();
        }
        // 回退到 RequestContext
        String requestTenantId = RequestContext.getTenantId();
        return requestTenantId != null ? requestTenantId : SystemConstants.DEFAULT_TENANT_ID;
    }

    /**
     * 获取指定字段值（单值，便捷方法）。
     *
     * @param claim 字段名（JWT claim 名）
     * @return 值，不存在返回 null
     */
    public static String getFieldValue(String claim) {
        TenantContext context = HOLDER.get();
        return context != null ? context.getFieldValue(claim) : null;
    }

    /**
     * 获取指定字段值（多值，便捷方法）。
     *
     * @param claim 字段名
     * @return 值列表，不存在返回空列表
     */
    public static List<String> getFieldValues(String claim) {
        TenantContext context = HOLDER.get();
        return context != null ? context.getFieldValues(claim) : Collections.emptyList();
    }

    /**
     * 是否为系统租户。
     *
     * @return true=系统租户，未设置返回 false
     */
    public static boolean isSystem() {
        TenantContext context = HOLDER.get();
        return context != null && context.isSystemTenant();
    }

    /**
     * 是否为超级管理员。
     *
     * @return true=超级管理员，未设置返回 false
     */
    public static boolean isSuperAdmin() {
        TenantContext context = HOLDER.get();
        return context != null && context.isSuperAdmin();
    }

    /**
     * 是否跳过租户隔离。
     *
     * @return true=跳过隔离，未设置返回 false
     */
    public static boolean isSkipped() {
        TenantContext context = HOLDER.get();
        return context != null && context.isSkipIsolation();
    }

    /**
     * 获取当前上下文的快照（用于异步传播）。
     *
     * @return 当前上下文副本，未设置返回 null
     */
    public static TenantContext snapshot() {
        return HOLDER.get();
    }

    /**
     * 恢复上下文（用于异步传播）。
     *
     * @param snapshot 之前快照的上下文
     */
    public static void restore(TenantContext snapshot) {
        if (snapshot != null) {
            HOLDER.set(snapshot);
        }
    }

    /**
     * 清除当前线程的租户上下文。
     *
     * <p>在请求结束、异步任务完成时必须调用，防止线程复用导致上下文泄漏。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
