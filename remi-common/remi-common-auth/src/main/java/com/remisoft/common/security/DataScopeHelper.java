package com.remisoft.common.security;

import com.remisoft.common.auth.context.AuthContextUtils;

/**
 * 数据权限 SQL 片段构造器。
 *
 * <p>从 {@link LoginUser} 上下文（ThreadLocal，由 {@link AuthContextUtils} 管理）提取当前用户的数据权限范围，
 * 构造可拼接到业务 SQL 的 WHERE 片段，实现行级数据权限过滤。
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>{@code DataScopeAspect} 在 Controller 入口拦截，将数据权限规则写入 ThreadLocal</li>
 *   <li>业务 Service 调用 {@link #buildSqlFragment(String, String, String, String)} 获取 SQL 片段</li>
 *   <li>Mapper XML 将 SQL 片段拼接到 WHERE 子句</li>
 *   <li>请求结束 {@code DataScopeAspect} 清理 ThreadLocal</li>
 * </ol>
 *
 * <p><b>数据权限规则（与 LoginUser.dataScope 对齐）：</b>
 * <ul>
 *   <li>{@code ALL} - 全部数据（管理员，返回空字符串表示不限制）</li>
 *   <li>{@code DEPT} - 本部门数据（{@code dept_id = ?}）</li>
 *   <li>{@code DEPT_AND_CHILD} - 本部门及下级部门数据（{@code dept_id IN (...)}，使用 deptIds 列表）</li>
 *   <li>{@code SELF} - 仅本人数据（{@code initiator_id = ?}）</li>
 *   <li>{@code CUSTOM} - 自定义数据权限（使用 customDeptIds 列表）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * String sqlFragment = DataScopeHelper.buildSqlFragment("", "", "dept_id", "initiator_id");
 * // sqlFragment 可能返回: "AND dept_id = '100'"
 * // 或无登录上下文时返回: ""
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class DataScopeHelper {

    private DataScopeHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构造数据权限 SQL 片段。
     *
     * <p>根据当前登录用户的数据权限规则，生成可拼接到 WHERE 子句的 SQL 片段。
     * 无登录上下文或权限规则为 ALL 时返回空字符串（不限制）。
     *
     * @param tableAlias    表别名（如 "t1."，可为空）
     * @param deptAlias     部门字段别名（保留参数，当前实现忽略）
     * @param deptColumn    部门字段名（如 "dept_id"）
     * @param userColumn    用户字段名（如 "initiator_id" / "created_by"）
     * @return SQL 片段（如 "AND t1.dept_id = '100'"），无权限限制时返回空字符串
     */
    public static String buildSqlFragment(String tableAlias, String deptAlias,
                                          String deptColumn, String userColumn) {
        LoginUser loginUser = AuthContextUtils.getCurrentOrNull();
        if (loginUser == null) {
            return "";
        }
        // 超级管理员 → 不限制
        if (loginUser.isSuperAdmin()) {
            return "";
        }
        String dataScope = loginUser.getDataScope();
        if (dataScope == null || dataScope.isBlank() || "ALL".equalsIgnoreCase(dataScope)) {
            return "";
        }
        String prefix = tableAlias == null || tableAlias.isBlank() ? "" : tableAlias;
        String deptCol = prefix + (deptColumn == null ? "dept_id" : deptColumn);
        String userCol = prefix + (userColumn == null ? "created_by" : userColumn);
        String userId = loginUser.getUserId();
        String deptId = loginUser.getDeptId();

        return switch (dataScope.toUpperCase()) {
            case "SELF" -> userId == null || userId.isBlank()
                    ? ""
                    : " AND " + userCol + " = '" + escapeSql(userId) + "'";
            case "DEPT" -> deptId == null || deptId.isBlank()
                    ? ""
                    : " AND " + deptCol + " = '" + escapeSql(deptId) + "'";
            case "DEPT_AND_CHILD" -> {
                if (loginUser.getDeptIds() == null || loginUser.getDeptIds().isEmpty()) {
                    yield "";
                }
                String inList = loginUser.getDeptIds().stream()
                        .map(DataScopeHelper::escapeSql)
                        .map(s -> "'" + s + "'")
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
                yield inList.isBlank() ? "" : " AND " + deptCol + " IN (" + inList + ")";
            }
            case "CUSTOM" -> {
                if (loginUser.getCustomDeptIds() == null || loginUser.getCustomDeptIds().isEmpty()) {
                    yield "";
                }
                String inList = loginUser.getCustomDeptIds().stream()
                        .map(DataScopeHelper::escapeSql)
                        .map(s -> "'" + s + "'")
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
                yield inList.isBlank() ? "" : " AND " + deptCol + " IN (" + inList + ")";
            }
            default -> ""; // 未知规则 → 不限制（由业务侧自行处理）
        };
    }

    /**
     * SQL 字符串转义（防 SQL 注入）。
     *
     * <p>仅保留字母、数字、下划线、连字符，其他字符一律删除。
     * 雪花算法 ID 仅包含数字，正常情况下无需转义，此处作为防御性编程。
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private static String escapeSql(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
