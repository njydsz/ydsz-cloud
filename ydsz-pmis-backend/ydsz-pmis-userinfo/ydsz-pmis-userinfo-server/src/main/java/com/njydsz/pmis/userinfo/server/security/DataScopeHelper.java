package com.njydsz.pmis.userinfo.server.security;

import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.security.LoginUser;

/**
 * 数据权限 SQL 片段构造器（userinfo 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-pmis-common-security 包，因 common 重构后该工具类已迁移到各业务模块本地化。
 * 配合 {@code @DataScope} 注解使用：根据当前用户的 {@code dataScope}（ALL / DEPT / DEPT_AND_CHILD / CUSTOM / SELF）
 * 生成对应的 SQL WHERE 片段，拼接到 MyBatis {@code LambdaQueryWrapper.apply(...)}。
 *
 * <p>使用示例：
 * <pre>{@code
 * String ds = DataScopeHelper.buildSqlFragment("dept_id", "id");
 * if (!ds.isEmpty()) wrapper.apply(ds);
 * }</pre>
 *
 * <p>实现说明：本版本基于 {@link AuthContext#getCurrentOrNull()} 解析当前用户身份，
 * 不再依赖 {@code TenantContext}；同时返回的 SQL 片段中预留的列名为「占位符」，
 * 由调用方在 {@code apply} 时通过 MyBatis 的列名映射机制替换为别名后的实际列名。
 *
 * @since 1.0.0
 */
public final class DataScopeHelper {

    private DataScopeHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构造数据权限 SQL 片段
     *
     * @param deptColumn 部门列名（如 {@code "dept_id"} / {@code "department_id"}）
     * @param userColumn 用户列名（如 {@code "id"} / {@code "created_by"}）
     * @return SQL 片段（如 {@code "dept_id = '1'"}），无权限时返回空串
     */
    public static String buildSqlFragment(String deptColumn, String userColumn) {
        return buildSqlFragment("", "", deptColumn, userColumn);
    }

    /**
     * 构造数据权限 SQL 片段（带表别名与用户列别名重载）
     *
     * @param tableAlias 表别名（当前实现未使用，预留用于后续多表 JOIN 场景）
     * @param userAlias  用户列别名（当前实现未使用）
     * @param deptColumn 部门列名
     * @param userColumn 用户列名
     * @return SQL 片段，无权限时返回空串
     */
    public static String buildSqlFragment(String tableAlias, String userAlias,
                                          String deptColumn, String userColumn) {
        LoginUser user = AuthContext.getCurrentOrNull();
        if (user == null) {
            return "";
        }
        String dataScope = user.getDataScope();
        switch (dataScope == null ? "ALL" : dataScope.toUpperCase()) {
            case "ALL":
                return "";
            case "DEPT":
                String deptId = user.getDeptId();
                return deptId == null ? "" : deptColumn + " = '" + escape(deptId) + "'";
            case "SELF":
                String userId = user.getUserId();
                return userId == null ? "" : userColumn + " = '" + escape(userId) + "'";
            case "CUSTOM":
            case "DEPT_AND_CHILD":
            default:
                // 复杂模式由业务层通过 NameAssembler 解析后注入 SQL，避免在此处拼装高风险 IN 子句
                return "";
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
