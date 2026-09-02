package com.njydsz.common.jdbc.constant;

/**
 * HTTP 请求头常量 — 数据权限域。
 *
 * <p>定义与行级/列级数据权限过滤相关的 HTTP Header 名称常量。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DataPermissionHeaderConstants {

  private DataPermissionHeaderConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** 数据权限范围类型（tenant/group/company/user/project/region/custom）。 */
  public static final String X_DATA_SCOPE = "X-Data-Scope";

  /** 租户ID。当数据权限范围为租户类型（TENANT）时使用。 */
  public static final String X_TENANT_ID = "X-Tenant-Id";

  /** 当前登录用户唯一标识。当数据权限范围为用户类型（USER）时使用。 */
  public static final String X_UNIQUE_ID = "X-Unique-Id";

  /** 公司ID集合（CSV）。当数据权限范围为集团类型（GROUP）时使用。 */
  public static final String X_COMPANY_IDS = "X-Company-Ids";

  /** 部门ID集合（CSV）。当数据权限范围为公司/部门类型时使用。 */
  public static final String X_DEPT_IDS = "X-Dept-Ids";

  /** 项目ID集合（CSV）。当数据权限范围为项目类型（PROJECT）时使用。 */
  public static final String X_PROJECT_IDS = "X-Project-Ids";

  /** 区域ID集合（CSV）。当数据权限范围为区域类型（REGION）时使用。 */
  public static final String X_REGION_IDS = "X-Region-Ids";

  /**
   * 自定义 SQL 条件标识。
   *
   * <p><b>安全警告：</b>仅传递标识键，不直接传递 SQL 片段。
   */
  public static final String X_CUSTOM_SQL_CONDITION = "X-Custom-Sql-Condition";

  /** 列级权限：表级可见列规则。格式：table:col1,col2;table2:col3 */
  public static final String X_VISIBLE_COLUMNS = "X-Visible-Columns";

  /** 列级权限：表级可编辑列规则。格式：table:col1,col2;table2:col3 */
  public static final String X_EDITABLE_COLUMNS = "X-Editable-Columns";

  /** 列级权限：签名值（HMAC-SHA256）。 */
  public static final String X_COL_PERMISSION_SIGN = "X-Col-Permission-Sign";
}
