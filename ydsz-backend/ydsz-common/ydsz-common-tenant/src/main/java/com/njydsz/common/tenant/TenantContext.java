package com.njydsz.common.tenant;

import java.util.Collections;
import java.util.Map;

/**
 * 租户上下文值对象（不可变）。
 *
 * <p>携带当前请求的完整租户维度信息，贯穿整个调用链。
 * 替代 {@code RequestContext.getTenantId()} + {@code AuthInfoUtils.getTenantId()} 双路径。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 普通用户请求
 * TenantContext ctx = TenantContext.of("tenant_001");
 *
 * // 系统租户（定时任务/MQ Consumer）
 * TenantContext ctx = TenantContext.system("0");
 *
 * // 跳过隔离（登录/注册等公开接口）
 * TenantContext ctx = TenantContext.skip();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TenantContext {

    /** 主租户 ID */
    private final String tenantId;

    /** 多级租户维度（MULTI 模式下有多个值） */
    private final Map<TenantDimension, String> dimensions;

    /** 是否系统租户（定时任务/MQ Consumer/内部调用） */
    private final boolean systemTenant;

    /** 是否超级管理员（可跨租户操作） */
    private final boolean superAdmin;

    /** 是否跳过租户隔离（登录/注册等公开接口） */
    private final boolean skipIsolation;

    private TenantContext(String tenantId, Map<TenantDimension, String> dimensions,
                          boolean systemTenant, boolean superAdmin, boolean skipIsolation) {
        this.tenantId = tenantId;
        this.dimensions = dimensions != null ? Collections.unmodifiableMap(dimensions) : Collections.emptyMap();
        this.systemTenant = systemTenant;
        this.superAdmin = superAdmin;
        this.skipIsolation = skipIsolation;
    }

    /**
     * 创建普通租户上下文。
     *
     * @param tenantId 租户 ID
     * @return 租户上下文
     */
    public static TenantContext of(String tenantId) {
        return new TenantContext(tenantId, Collections.emptyMap(), false, false, false);
    }

    /**
     * 创建系统租户上下文（定时任务/MQ Consumer 使用）。
     *
     * @param systemTenantId 系统租户 ID
     * @return 系统租户上下文
     */
    public static TenantContext system(String systemTenantId) {
        return new TenantContext(systemTenantId, Collections.emptyMap(), true, false, false);
    }

    /**
     * 创建跳过隔离的上下文（登录/注册等公开接口）。
     *
     * @return 跳过隔离的上下文
     */
    public static TenantContext skip() {
        return new TenantContext(null, Collections.emptyMap(), false, false, true);
    }

    /**
     * 创建空上下文。
     *
     * @return 空上下文
     */
    public static TenantContext empty() {
        return new TenantContext(null, Collections.emptyMap(), false, false, false);
    }

    /**
     * 创建 Builder 用于多级租户上下文。
     *
     * @param tenantId 主租户 ID
     * @return Builder
     */
    public static Builder builder(String tenantId) {
        return new Builder(tenantId);
    }

    /**
     * 获取主租户 ID。
     *
     * @return 租户 ID，可能为 null
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 获取多级维度值。
     *
     * @param dimension 维度
     * @return 维度值，不存在返回 null
     */
    public String getDimension(TenantDimension dimension) {
        return dimensions.get(dimension);
    }

    /**
     * 获取所有维度。
     *
     * @return 不可变维度 Map
     */
    public Map<TenantDimension, String> getDimensions() {
        return dimensions;
    }

    /**
     * 是否为系统租户。
     *
     * @return true=系统租户
     */
    public boolean isSystemTenant() {
        return systemTenant;
    }

    /**
     * 是否为超级管理员。
     *
     * @return true=超级管理员
     */
    public boolean isSuperAdmin() {
        return superAdmin;
    }

    /**
     * 是否跳过租户隔离。
     *
     * @return true=跳过
     */
    public boolean isSkipIsolation() {
        return skipIsolation;
    }

    /**
     * 是否为空上下文（无租户 ID 且非跳过）。
     *
     * @return true=空上下文
     */
    public boolean isEmpty() {
        return tenantId == null && !skipIsolation;
    }

    /**
     * Builder 模式构建多级租户上下文。
     */
    public static final class Builder {

        private final String tenantId;
        private final Map<TenantDimension, String> dimensions = new java.util.HashMap<>();
        private boolean systemTenant;
        private boolean superAdmin;
        private boolean skipIsolation;

        private Builder(String tenantId) {
            this.tenantId = tenantId;
        }

        /**
         * 添加维度值。
         *
         * @param dimension 维度
         * @param value     值
         * @return this
         */
        public Builder dimension(TenantDimension dimension, String value) {
            if (dimension != null && value != null) {
                dimensions.put(dimension, value);
            }
            return this;
        }

        /**
         * 设置是否系统租户。
         *
         * @param systemTenant 是否系统租户
         * @return this
         */
        public Builder systemTenant(boolean systemTenant) {
            this.systemTenant = systemTenant;
            return this;
        }

        /**
         * 设置是否超级管理员。
         *
         * @param superAdmin 是否超级管理员
         * @return this
         */
        public Builder superAdmin(boolean superAdmin) {
            this.superAdmin = superAdmin;
            return this;
        }

        /**
         * 设置是否跳过隔离。
         *
         * @param skipIsolation 是否跳过
         * @return this
         */
        public Builder skipIsolation(boolean skipIsolation) {
            this.skipIsolation = skipIsolation;
            return this;
        }

        /**
         * 构建不可变上下文。
         *
         * @return 租户上下文
         */
        public TenantContext build() {
            return new TenantContext(tenantId, dimensions, systemTenant, superAdmin, skipIsolation);
        }
    }
}
