package com.njydsz.common.base.constant;

/**
 * HTTP 请求头常量（base 模块内部使用）
 *
 * <p>集中维护 OpenAPI 文档展示所需的请求头名称常量， 避免 base 模块对 common-auth / common-jdbc 模块的硬依赖。
 *
 * <p>工具类不允许实例化。
 *
 * <p><b>线程安全性：</b>所有字段均为 {@code public static final}，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class HttpHeaderConstants {

  /**
   * 私有构造方法，工具类禁止实例化。
   *
   * @throws IllegalStateException 任何实例化尝试都会抛出
   */
  private HttpHeaderConstants() {
    throw new IllegalStateException("Utility class");
  }

  // ========================================================================
  // 认证/身份域
  // ========================================================================

  /** 登录访问令牌。用户登录后颁发的 AccessToken。 */
  public static final String X_ACCESS_TOKEN = "X-Access-Token";

  /** 用户系统语言。格式示例：zh-CN、en-US。 */
  public static final String X_USER_LANGUAGE = "X-User-Language";

  /** 用户设备唯一标识。用于设备追踪与多端识别。 */
  public static final String X_DISTINCT_ID = "X-Distinct-Id";

  /** 服务类型。用于区分请求来源服务类型（WEB_SERVICE / APP_SERVICE）。 */
  public static final String X_SERVICE_TYPE = "X-Service-Type";

  // ========================================================================
  // 数据权限域
  // ========================================================================

  /** 当前登录用户唯一标识。当数据权限范围为用户类型（USER）时使用。 */
  public static final String X_UNIQUE_ID = "X-Unique-Id";

  /** 数据权限范围类型（tenant/group/company/user/project/region/custom）。 */
  public static final String X_DATA_SCOPE = "X-Data-Scope";

  /** 租户ID。当数据权限范围为租户类型（TENANT）时使用。 */
  public static final String X_TENANT_ID = "X-Tenant-Id";

  /** 公司ID集合（CSV）。当数据权限范围为集团类型（GROUP）时使用。 */
  public static final String X_COMPANY_IDS = "X-Company-Ids";

  /** 部门ID集合（CSV）。当数据权限范围为公司/部门类型时使用。 */
  public static final String X_DEPT_IDS = "X-Dept-Ids";

  /** 项目ID集合（CSV）。当数据权限范围为项目类型（PROJECT）时使用。 */
  public static final String X_PROJECT_IDS = "X-Project-Ids";

  /** 区域ID集合（CSV）。当数据权限范围为区域类型（REGION）时使用。 */
  public static final String X_REGION_IDS = "X-Region-Ids";

  /** 列级权限：表级可见列规则。格式：table:col1,col2;table2:col3 */
  public static final String X_VISIBLE_COLUMNS = "X-Visible-Columns";

  /** 列级权限：表级可编辑列规则。格式：table:col1,col2;table2:col3 */
  public static final String X_EDITABLE_COLUMNS = "X-Editable-Columns";
}
