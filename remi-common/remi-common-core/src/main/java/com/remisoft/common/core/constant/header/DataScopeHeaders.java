package com.remisoft.common.core.constant.header;

/**
 * 数据权限（行级）相关 HTTP 请求头常量
 *
 * <p>定义多租户数据权限、组织维度可见性控制等 header。
 *
 * <p>对应模块：remi-common-auth（@RbacDataScope 切面写入）、remi-common-jdbc（SQL 拦截器读取改写）
 *
 * @author remi-team
 * @since 1.8.0
 * @see ColumnPermissionHeaders
 */
public final class DataScopeHeaders {

    private DataScopeHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 数据权限范围类型
     *
     * <p>配合维度ID类 header 使用：
     * <ul>
     *   <li>tenant：配合 {@link #X_TENANT_ID}</li>
     *   <li>group：配合 {@link #X_COMPANY_IDS}</li>
     *   <li>company/dept：配合 {@link #X_DEPT_IDS}</li>
     *   <li>user：配合 {@link #X_UNIQUE_ID}</li>
     *   <li>project：配合 {@link #X_PROJECT_IDS}</li>
     *   <li>region：配合 {@link #X_REGION_IDS}</li>
     * </ul>
     *
     * <p>当此 header 存在时，SQL 拦截器优先按该 scope 对应维度过滤；
     * 当不携带时，拦截器会按所有非空维度叠加（取交集）。
     */
    public static final String X_DATA_SCOPE = "X-Data-Scope";

    /**
     * 租户ID
     *
     * <p>当数据权限范围为租户类型（TENANT）时，此 header 作为行级过滤条件。
     */
    public static final String X_TENANT_ID = "X-Tenant-Id";

    /**
     * 当前登录用户唯一标识
     *
     * <p>当数据权限范围为用户类型（USER）时，此 header 作为行级过滤条件。
     */
    public static final String X_UNIQUE_ID = "X-Unique-Id";

    /**
     * 公司ID集合（CSV）
     *
     * <p>当数据权限范围为集团类型（GROUP）时，此 header 包含用户可访问的所有公司ID。
     * 格式：逗号分隔（如 {@code 1001,1002}），也允许多 header 值合并。
     */
    public static final String X_COMPANY_IDS = "X-Company-Ids";

    /**
     * 部门ID集合（CSV）
     *
     * <p>当数据权限范围为公司/部门类型（COMPANY/DEPT）时，此 header 包含用户可访问的所有部门ID。
     * 格式：逗号分隔（如 {@code 2001,2002}），也允许多 header 值合并。
     */
    public static final String X_DEPT_IDS = "X-Dept-Ids";

    /**
     * 项目ID集合（CSV）
     *
     * <p>当数据权限范围为项目类型（PROJECT）时，此 header 包含用户可访问的所有项目ID。
     * 格式：逗号分隔，也允许多 header 值合并。
     */
    public static final String X_PROJECT_IDS = "X-Project-Ids";

    /**
     * 区域ID集合（CSV）
     *
     * <p>当数据权限范围为区域类型（REGION）时，此 header 包含用户可访问的所有区域ID。
     * 格式：逗号分隔，也允许多 header 值合并。
     */
    public static final String X_REGION_IDS = "X-Region-Ids";

    /**
     * 自定义 SQL 条件标识
     *
     * <p>当数据权限范围为自定义类型（CUSTOM）时，此 header 携带自定义数据权限的标识键，
     * 由服务端数据权限 Provider 根据此标识生成安全的 SQL 条件片段。
     *
     * <p><b>安全警告：</b>此 header 仅传递标识键，不直接传递 SQL 片段。
     * SQL 条件由服务端 Provider 生成，禁止将原始 SQL 通过 HTTP 请求传入，
     * 以防止 SQL 注入攻击。
     */
    public static final String X_CUSTOM_SQL_CONDITION = "X-Custom-Sql-Condition";
}
