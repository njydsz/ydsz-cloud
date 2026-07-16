package com.njydsz.common.jdbc.permission;

/**
 * 数据权限上下文。
 *
 * <p>封装当前请求的数据权限信息，包括租户ID、用户ID、
 * 公司ID列表、部门ID列表、项目ID列表、区域ID列表等，
 * 用于 SQL 拦截器自动拼接数据权限过滤条件。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.core.enums.DataScopeType;

import lombok.Data;

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
     * 租户隔离是否启用（由配置类构造时注入）。
     */
    private boolean tenantIsolationEnabled = true;

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
     * @return true 表示启用租户隔离，false 表示关闭
     */
    public boolean isTenantIsolationEnabled() {
        return tenantIsolationEnabled;
    }

    /**
     * 设置租户隔离是否启用。
     *
     * @param tenantIsolationEnabled 租户隔离是否启用
     */
    public void setTenantIsolationEnabled(boolean tenantIsolationEnabled) {
        this.tenantIsolationEnabled = tenantIsolationEnabled;
    }
}
