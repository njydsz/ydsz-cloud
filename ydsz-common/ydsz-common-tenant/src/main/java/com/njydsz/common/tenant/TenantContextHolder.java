package com.njydsz.common.tenant;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.ContextKey;
import com.njydsz.common.core.context.RequestContext;

/**
 * 租户上下文持有者 — 全模块唯一的类型安全读写入口。
 *
 * <p>基于 {@link ContextKey} 提供编译期类型保证，统一替代以下双路径：
 * <ul>
 *   <li>_object path_：{@code RequestContext.put(KEY_TENANT_CONTEXT, ctx)} +
 *       {@code (TenantContext) RequestContext.get(KEY_TENANT_CONTEXT)}</li>
 *   <li>_string path_：{@code RequestContext.setTenantId(id)} /
 *       {@code RequestContext.getTenantId()}</li>
 * </ul>
 *
 * <p><b>唯一写入口：</b>{@link #set(TenantContext)} 同时向 RequestContext 注入
 * {@link BizContextKeys#KEY_TENANT_CONTEXT}（主路径）并同步 {@code tenantId}
 * 字符串（兼容 {@code RequestContext.bridgeToMdc()} 等已存在读取方）。
 *
 * <p><b>唯一读入口：</b>{@link #get()}、{@link #getTenantId()}、{@link #isSuperAdmin()}、
 * {@link #isSkipIsolation()} 等，全部从 {@link BizContextKeys#KEY_TENANT_CONTEXT} 派生。
 *
 * <p><b>生命周期：</b>请求结束时必须调用 {@link #clear()}，推荐 try-with-resources 或
 * Filter/Interceptor 的 finally 块。
 *
 * @author ydsz-team
 * @since 1.10.0
 */
public final class TenantContextHolder {

    /** 类型安全键，避免字符串字面量散落 + 编译期类型保证。 */
    public static final ContextKey<TenantContext> KEY =
            ContextKey.of(BizContextKeys.KEY_TENANT_CONTEXT, TenantContext.class);

    private TenantContextHolder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 设置租户上下文（唯一写入口）。
     *
     * <p>同时同步 {@code tenantId} 字符串到 RequestContext，确保
     * {@link RequestContext#bridgeToMdc()} 等已有读取方正常工作。
     *
     * @param context 租户上下文，传入 {@code null} 等同于 {@link #clear()}
     */
    public static void set(TenantContext context) {
        if (context == null) {
            clear();
            return;
        }
        RequestContext.put(KEY.key(), context);
        // 同步 tenantId 字符串，兼容 bridgeToMdc() 等已有读取方
        RequestContext.setTenantId(context.getTenantId());
    }

    /**
     * 获取租户上下文（类型安全，无需强转）。
     *
     * @return 当前租户上下文，不存在返回 {@code null}
     */
    public static TenantContext get() {
        return KEY.cast(RequestContext.get(KEY.key()));
    }

    /**
     * 获取主租户 ID（从上下文派生，非独立存储）。
     *
     * @return 租户 ID，上下文不存在返回 {@code null}
     */
    public static String getTenantId() {
        TenantContext ctx = get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    /**
     * 是否已设置租户上下文。
     *
     * @return true=已设置
     */
    public static boolean isPresent() {
        return RequestContext.has(KEY);
    }

    /**
     * 当前租户是否跳过隔离。
     *
     * @return true=跳过隔离（登录/注册等公开接口）
     */
    public static boolean isSkipIsolation() {
        TenantContext ctx = get();
        return ctx != null && ctx.isSkipIsolation();
    }

    /**
     * 当前租户是否为超级管理员。
     *
     * @return true=超级管理员（可跨租户操作）
     */
    public static boolean isSuperAdmin() {
        TenantContext ctx = get();
        return ctx != null && ctx.isSuperAdmin();
    }

    /**
     * 是否为系统租户（定时任务/MQ Consumer/内部调用）。
     *
     * @return true=系统租户
     */
    public static boolean isSystemTenant() {
        TenantContext ctx = get();
        return ctx != null && ctx.isSystemTenant();
    }

    /**
     * 清除租户上下文（含 tenantId 字符串同步清理）。
     */
    public static void clear() {
        RequestContext.remove(KEY.key());
        RequestContext.remove(RequestContext.KEY_TENANT_ID);
    }

    /**
     * 获取当前快照（用于异步传播）。
     *
     * @return 上下文快照，不存在返回 {@code null}
     */
    public static TenantContext snapshot() {
        TenantContext ctx = get();
        return ctx != null ? ctx.snapshot() : null;
    }

    /**
     * 在租户上下文中执行逻辑，执行后自动清理。
     *
     * <p>用于无用户上下文的场景（定时任务、MQ Consumer）。
     *
     * @param context  预设的租户上下文
     * @param runnable 待执行逻辑
     */
    public static void runWithContext(TenantContext context, Runnable runnable) {
        set(context);
        try {
            runnable.run();
        } finally {
            clear();
        }
    }
}
