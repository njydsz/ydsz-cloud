package com.njydsz.common.tenant.spi;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.tenant.TenantContext;

/**
 * 租户上下文持有者接口。
 *
 * <p><b>已废弃</b>：自 1.1.0 起，推荐直接使用 {@link RequestContext} API 访问租户上下文：
 * <pre>{@code
 * // 获取租户 ID
 * String tenantId = RequestContext.getTenantId();
 *
 * // 获取完整租户上下文
 * TenantContext ctx = RequestContext.getTenantContext();
 *
 * // 设置租户上下文
 * RequestContext.put(BizContextKeys.KEY_TENANT_CONTEXT, TenantContext.of("tenant_001"));
 * }</pre>
 *
 * <p>此接口保留仅为向后兼容。所有 default 方法内部委托到 {@link RequestContext}，
 * 不再需要业务模块提供实现类。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 1.1.0 起废弃，使用 {@link RequestContext} 直接 API 替代。
 *             计划在 2.0.0 移除。
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public interface TenantContextHolder {

    /**
     * 获取当前租户 ID。
     *
     * @return 租户 ID，未设置时返回 null
     *
     * @deprecated 使用 {@link RequestContext#getTenantId()} 替代
     */
    @Deprecated(since = "1.1.0")
    default String getTenantId() {
        return RequestContext.getTenantId();
    }

    /**
     * 获取完整租户上下文。
     *
     * @return 租户上下文，未设置时返回 null
     *
     * @deprecated 使用 {@link RequestContext#getTenantContext()} 替代
     * @since 1.1.0
     */
    @Deprecated(since = "1.1.0")
    default TenantContext getTenantContext() {
        return (TenantContext) RequestContext.get(BizContextKeys.KEY_TENANT_CONTEXT);
    }

    /**
     * 判断是否为超级管理员租户。
     *
     * @return true=超级管理员，false-普通租户
     *
     * @deprecated 使用 {@code getTenantContext().isSuperAdmin()} 替代
     */
    @Deprecated(since = "1.1.0")
    default boolean isSuperTenant() {
        String tenantId = getTenantId();
        return tenantId == null || "0".equals(tenantId);
    }

    /**
     * 判断是否为系统租户上下文（异步/定时任务场景）。
     *
     * @return true=系统租户，false=普通用户请求
     *
     * @since 1.1.0
     */
    @Deprecated(since = "1.1.0")
    default boolean isSystemTenant() {
        TenantContext ctx = getTenantContext();
        return ctx != null && ctx.isSystemTenant();
    }
}
