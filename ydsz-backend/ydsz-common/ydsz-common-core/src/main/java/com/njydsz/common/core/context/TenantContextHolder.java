package com.njydsz.common.core.context;

/**
 * 租户上下文持有者接口
 *
 * <p>用于在公共模块中获取当前租户 ID，避免循环依赖。
 * 具体实现由业务模块（如 ydsz-common-auth）提供。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TenantContextHolder {

    /**
     * 获取当前租户 ID
     *
     * @return 租户 ID，未设置时返回 null
     */
    String getTenantId();

    /**
     * 判断是否为超级管理员租户
     *
     * @return true-超级管理员，false-普通租户
     */
    default boolean isSuperTenant() {
        String tenantId = getTenantId();
        return tenantId == null || "0".equals(tenantId);
    }
}
