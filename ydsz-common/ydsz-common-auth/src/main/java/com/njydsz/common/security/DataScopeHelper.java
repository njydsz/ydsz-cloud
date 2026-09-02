package com.njydsz.common.security;

import java.util.regex.Pattern;

import com.njydsz.common.auth.context.AuthContextUtils;

/**
 * 数据权限 SQL 片段构造器。
 *
 * <p>从 {@link LoginUser} 上下文（ThreadLocal，由 {@link AuthContextUtils} 管理）提取当前用户的数据权限范围， 构造可拼接到业务 SQL
 * 的 WHERE 片段，实现行级数据权限过滤。
 *
 * <p><b>工作流程：</b>
 *
 * <ol>
 *   <li>{@code DataScopeAspect} 在 Controller 入口拦截，将数据权限规则写入 ThreadLocal
 *   <li>业务 Service 调用 {@link #buildSqlFragment(String, String, String, String)} 获取 SQL 片段
 *   <li>Mapper XML 将 SQL 片段拼接到 WHERE 子句
 *   <li>请求结束 {@code DataScopeAspect} 清理 ThreadLocal
 * </ol>
 *
 * <p><b>数据权限规则（与 LoginUser.dataScope 对齐）：</b>
 *
 * <ul>
 *   <li>{@code ALL} - 全部数据（管理员，返回空字符串表示不限制）
 *   <li>{@code DEPT} - 本部门数据（{@code dept_id = ?}）
 *   <li>{@code DEPT_AND_CHILD} - 本部门及下级部门数据（{@code dept_id IN (...)}，使用 deptIds 列表）
 *   <li>{@code SELF} - 仅本人数据（{@code initiator_id = ?}）
 *   <li>{@code CUSTOM} - 自定义数据权限（使用 customDeptIds 列表）
 *   <li>未知规则 - <b>fail-closed</b>：返回 {@code AND 1 = 0}（无任何数据）， 防止未识别的数据权限规则被当作「不限制」导致越权读取
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * String sqlFragment = DataScopeHelper.buildSqlFragment("", "", "dept_id", "initiator_id");
 * // sqlFragment 可能返回: "AND dept_id = '100'"
 * // 或无登录上下文时返回: ""
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DataScopeHelper {

  private DataScopeHelper() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 构造数据权限 SQL 片段。
   *
   * <p>根据当前登录用户的数据权限规则，生成可拼接到 WHERE 子句的 SQL 片段。 无登录上下文或权限规则为 ALL 时返回空字符串（不限制）。
   *
   * @param tableAlias 表别名（如 "t1."，可为空）
   * @param deptAlias 部门字段别名（保留参数，当前实现忽略）
   * @param deptColumn 部门字段名（如 "dept_id"）
   * @param userColumn 用户字段名（如 "initiator_id" / "created_by"）
   * @return SQL 片段（如 "AND t1.dept_id = '100'"），无权限限制时返回空字符串
   */
  public static String buildSqlFragment(
      String tableAlias, String deptAlias, String deptColumn, String userColumn) {
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
    // 列名/别名白名单校验：仅允许标准 SQL 标识符（含可选尾部点号），杜绝拼接注入面
    validateSqlIdentifier(deptColumn == null ? "dept_id" : deptColumn, "deptColumn");
    validateSqlIdentifier(userColumn == null ? "created_by" : userColumn, "userColumn");
    validateTableAlias(tableAlias);
    String deptCol = prefix + (deptColumn == null ? "dept_id" : deptColumn);
    String userCol = prefix + (userColumn == null ? "created_by" : userColumn);
    String userId = loginUser.getUserId();
    String deptId = loginUser.getDeptId();

    return switch (dataScope.toUpperCase()) {
      case "SELF" ->
          userId == null || userId.isBlank()
              ? ""
              : " AND " + userCol + " = '" + escapeSql(userId) + "'";
      case "DEPT" ->
          deptId == null || deptId.isBlank()
              ? ""
              : " AND " + deptCol + " = '" + escapeSql(deptId) + "'";
      case "DEPT_AND_CHILD" -> {
        if (loginUser.getDeptIds() == null || loginUser.getDeptIds().isEmpty()) {
          yield "";
        }
        String inList =
            loginUser.getDeptIds().stream()
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
        String inList =
            loginUser.getCustomDeptIds().stream()
                .map(DataScopeHelper::escapeSql)
                .map(s -> "'" + s + "'")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        yield inList.isBlank() ? "" : " AND " + deptCol + " IN (" + inList + ")";
      }
        // fail-closed：未知/未识别的数据权限规则按「无权限」处理，
        // 返回一个恒为 false 的过滤条件，防止越权读取全量数据。
        // 业务侧若需新增规则，必须在此显式注册后再使用。
      default -> " AND 1 = 0";
    };
  }

  /**
   * SQL 字符串转义（防 SQL 注入）。
   *
   * <p>仅保留字母、数字、下划线、连字符，其他字符一律删除。 雪花算法 ID 仅包含数字，正常情况下无需转义，此处作为防御性编程。
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

  /** SQL 标识符白名单模式：字母/下划线开头，后跟字母/数字/下划线（列名、字段名） */
  private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

  /** 表别名白名单模式：合法标识符后跟可选点号（如 t1、t1.） */
  private static final Pattern TABLE_ALIAS_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*\\.?$");

  /**
   * 校验 SQL 列名/字段名为合法标识符（fail-closed）。
   *
   * <p>数据权限列名通常来源于注解常量，此处作为纵深防御， 防止列名被外部输入污染后拼接进 SQL 造成注入。 不合法时抛出
   * {@link IllegalArgumentException}，拒绝执行而非静默放行。
   *
   * @param column 列名
   * @param paramName 参数名（用于异常信息）
   * @throws IllegalArgumentException 列名不合法时抛出
   */
  private static void validateSqlIdentifier(String column, String paramName) {
    if (column == null || !SQL_IDENTIFIER_PATTERN.matcher(column).matches()) {
      throw new IllegalArgumentException(
          "数据权限参数非法: " + paramName + " 必须是合法 SQL 标识符, 实际值: " + column);
    }
  }

  /**
   * 校验表别名为合法标识符（可带尾部点号，fail-closed）。
   *
   * @param tableAlias 表别名
   * @throws IllegalArgumentException 别名不合法时抛出
   */
  private static void validateTableAlias(String tableAlias) {
    if (tableAlias == null || tableAlias.isBlank()) {
      return;
    }
    if (!TABLE_ALIAS_PATTERN.matcher(tableAlias).matches()) {
      throw new IllegalArgumentException(
          "数据权限参数非法: tableAlias 必须是合法 SQL 标识符(可带尾部点号), 实际值: " + tableAlias);
    }
  }
}
