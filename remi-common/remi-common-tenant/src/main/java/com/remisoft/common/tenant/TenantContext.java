package com.remisoft.common.tenant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 租户上下文值对象（不可变）。
 *
 * <p>携带当前请求的完整租户字段信息，贯穿整个调用链。
 * 字段完全动态，由配置的 {@code tenant-fields} 决定哪些字段存在。
 *
 * <p><b>字段值类型：</b>
 * <ul>
 *   <li>单值字段 → String</li>
 *   <li>多值字段 → List&lt;String&gt;</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 普通用户请求（单字段）
 * Map<String, Object> fields = Map.of("tenantId", "tenant_001");
 * TenantContext ctx = TenantContext.of(fields);
 *
 * // 多字段组合
 * Map<String, Object> fields = new HashMap<>();
 * fields.put("tenantId", "tenant_001");
 * fields.put("companyId", "comp_001");
 * fields.put("deptId", List.of("dept_001", "dept_002")); // 多值
 * TenantContext ctx = TenantContext.of(fields);
 *
 * // 系统租户（定时任务/MQ）
 * TenantContext ctx = TenantContext.system("0");
 *
 * // 跳过隔离（登录/注册）
 * TenantContext ctx = TenantContext.skip();
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class TenantContext {

    /** 主租户 ID（用于 Redis Key 隔离、MDC 日志、超级管理员判断） */
    private final String tenantId;

    /** 动态字段值（key=claim 名，value=String 或 List<String>） */
    private final Map<String, Object> fields;

    /** 是否系统租户（定时任务/MQ Consumer/内部调用） */
    private final boolean systemTenant;

    /** 是否超级管理员（可跨租户操作） */
    private final boolean superAdmin;

    /** 是否跳过租户隔离（登录/注册等公开接口） */
    private final boolean skipIsolation;

    private TenantContext(String tenantId, Map<String, Object> fields,
                          boolean systemTenant, boolean superAdmin, boolean skipIsolation) {
        this.tenantId = tenantId;
        this.fields = fields != null ? Collections.unmodifiableMap(fields) : Collections.emptyMap();
        this.systemTenant = systemTenant;
        this.superAdmin = superAdmin;
        this.skipIsolation = skipIsolation;
    }

    /**
     * 创建普通租户上下文。
     *
     * @param tenantId 主租户 ID
     * @param fields   动态字段值
     * @return 租户上下文
     */
    public static TenantContext of(String tenantId, Map<String, Object> fields) {
        return new TenantContext(tenantId, fields, false, false, false);
    }

    /**
     * 创建仅含 tenantId 的简单上下文（向后兼容）。
     *
     * @param tenantId 租户 ID
     * @return 租户上下文
     */
    public static TenantContext of(String tenantId) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("tenantId", tenantId);
        return new TenantContext(tenantId, fields, false, false, false);
    }

    /**
     * 创建系统租户上下文。
     *
     * @param systemTenantId 系统租户 ID
     * @return 系统租户上下文
     */
    public static TenantContext system(String systemTenantId) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("tenantId", systemTenantId);
        return new TenantContext(systemTenantId, fields, true, false, false);
    }

    /**
     * 创建跳过隔离的上下文。
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
     * 创建 Builder 用于多字段上下文。
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
     * 获取字段值（单值）。
     *
     * @param claim 字段名（JWT claim 名）
     * @return 单值，不存在返回 null
     */
    public String getFieldValue(String claim) {
        Object value = fields.get(claim);
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return null;
    }

    /**
     * 获取字段值（多值）。
     *
     * <p>通过遍历 {@code List<?>} 并对每个元素进行 {@code instanceof String}
     * 检查来构建 {@code List<String>}，避免使用 {@code @SuppressWarnings("unchecked")}
     * 注解和未经检查的强制类型转换。非 String 元素会被跳过。
     *
     * @param claim 字段名
     * @return 多值列表，单值时包装为单元素列表，不存在返回空列表
     */
    public List<String> getFieldValues(String claim) {
        Object value = fields.get(claim);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof String s) {
                    result.add(s);
                }
            }
            return result;
        }
        if (value instanceof String s) {
            return List.of(s);
        }
        return Collections.emptyList();
    }

    /**
     * 获取所有字段。
     *
     * @return 不可变字段 Map
     */
    public Map<String, Object> getFields() {
        return fields;
    }

    /**
     * 是否为系统租户。
     */
    public boolean isSystemTenant() {
        return systemTenant;
    }

    /**
     * 是否为超级管理员。
     */
    public boolean isSuperAdmin() {
        return superAdmin;
    }

    /**
     * 是否跳过租户隔离。
     */
    public boolean isSkipIsolation() {
        return skipIsolation;
    }

    /**
     * 是否为空上下文。
     */
    public boolean isEmpty() {
        return tenantId == null && !skipIsolation;
    }

    /**
     * 创建快照（用于异步传播）。
     *
     * @return 新的不可变实例
     */
    public TenantContext snapshot() {
        return new TenantContext(tenantId, new HashMap<>(fields), systemTenant, superAdmin, skipIsolation);
    }

    /**
     * Builder 模式构建多字段上下文。
     */
    public static final class Builder {

        private final String tenantId;
        private final Map<String, Object> fields = new HashMap<>();
        private boolean systemTenant;
        private boolean superAdmin;
        private boolean skipIsolation;

        private Builder(String tenantId) {
            this.tenantId = tenantId;
        }

        /**
         * 添加单值字段。
         *
         * @param claim 字段名
         * @param value 值
         * @return this
         */
        public Builder field(String claim, String value) {
            if (claim != null && value != null) {
                fields.put(claim, value);
            }
            return this;
        }

        /**
         * 添加多值字段。
         *
         * @param claim  字段名
         * @param values 值列表
         * @return this
         */
        public Builder fieldValues(String claim, List<String> values) {
            if (claim != null && values != null && !values.isEmpty()) {
                fields.put(claim, new ArrayList<>(values));
            }
            return this;
        }

        /**
         * 设置是否系统租户。
         */
        public Builder systemTenant(boolean systemTenant) {
            this.systemTenant = systemTenant;
            return this;
        }

        /**
         * 设置是否超级管理员。
         */
        public Builder superAdmin(boolean superAdmin) {
            this.superAdmin = superAdmin;
            return this;
        }

        /**
         * 设置是否跳过隔离。
         */
        public Builder skipIsolation(boolean skipIsolation) {
            this.skipIsolation = skipIsolation;
            return this;
        }

        /**
         * 构建不可变上下文。
         */
        public TenantContext build() {
            if (!fields.containsKey("tenantId") && tenantId != null) {
                fields.put("tenantId", tenantId);
            }
            return new TenantContext(tenantId, fields, systemTenant, superAdmin, skipIsolation);
        }
    }
}
