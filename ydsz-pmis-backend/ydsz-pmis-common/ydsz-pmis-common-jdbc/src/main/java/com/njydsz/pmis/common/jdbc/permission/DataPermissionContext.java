package com.njydsz.pmis.common.jdbc.permission;

/**
 * 数据权限上下文。
 *
 * <p>封装当前请求的数据权限信息，包括租户ID、用户ID、
 * 公司ID列表、部门ID列表、项目ID列表、区域ID列表等，
 * 用于 SQL 拦截器自动拼接数据权限过滤条件。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */

import com.njydsz.pmis.common.core.enums.DataScopeType;
import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class DataPermissionContext {
    /**
     * 行级权限维度（从请求头或 RequestHolder 解析）。
     */
    private DataScopeType dataScope;
    /** 租户ID */
    private String tenantId;
    /** 用户ID */
    private String userId;
    /** 公司ID集合 */
    private Set<String> companyIds = new HashSet<>();
    /** 部门ID集合 */
    private Set<String> deptIds = new HashSet<>();
    /** 项目ID集合 */
    private Set<String> projectIds = new HashSet<>();
    /** 区域ID集合 */
    private Set<String> regionIds = new HashSet<>();

    /**
     * 列级权限规则：table -> allowed columns（小写化后比对）。
     */
    private Map<String, Set<String>> visibleColumnsByTable = Collections.emptyMap();
    /**
     * 列级权限规则：table -> editable columns（小写化后比对）。
     */
    private Map<String, Set<String>> editableColumnsByTable = Collections.emptyMap();

    /**
     * 租户隔离开关 Supplier（由配置类初始化）。
     */
    private static volatile java.util.function.Supplier<Boolean> tenantIsolationEnabledSupplier = () -> true;

    /**
     * 初始化租户隔离开关的 Supplier。
     *
     * <p>由 Spring Boot 配置类在启动时调用，传入实际的配置读取逻辑。
     *
     * @param supplier 租户隔离开关的 Supplier
     */
    public static void initTenantIsolationEnabledSupplier(java.util.function.Supplier<Boolean> supplier) {
        tenantIsolationEnabledSupplier = supplier;
    }

    /**
     * 返回一个空的 DataPermissionContext 实例。
     *
     * @return 所有字段为默认空值的上下文
     */
    public static DataPermissionContext empty() {
        return new DataPermissionContext();
    }

    /**
     * 是否缺少全部行级约束信息。
     */
    public boolean isEmptyRowScope() {
        return dataScope == null
                && tenantId == null
                && userId == null
                && companyIds.isEmpty()
                && deptIds.isEmpty()
                && projectIds.isEmpty()
                && regionIds.isEmpty();
    }

    /**
     * 判断租户隔离是否启用。
     *
     * <p>通过外部配置的 Supplier 读取开关状态，默认返回 true（启用租户隔离）。
     *
     * @return true 表示启用租户隔离，false 表示关闭
     */
    public static boolean isTenantIsolationEnabled() {
        return tenantIsolationEnabledSupplier != null && tenantIsolationEnabledSupplier.get();
    }
}
