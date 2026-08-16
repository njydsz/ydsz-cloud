package com.njydsz.common.auth.constant;

/**
 * HTTP 请求头常量 — 认证/身份域。
 *
 * <p>定义与身份认证、令牌、用户身份相关的 HTTP Header 名称常量。
 *
 * @author ydsz-team
 * @since 1.11.0
 */
public final class AuthHeaderConstants {

  private AuthHeaderConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** 登录访问令牌。用户登录后颁发的 AccessToken。 */
  public static final String X_ACCESS_TOKEN = "X-Access-Token";

  /** 当前登录用户ID。由网关/认证服务在请求入口写入。 */
  public static final String X_USER_ID = "X-User-Id";

  /** 用户系统语言。格式示例：zh-CN、en-US。 */
  public static final String X_USER_LANGUAGE = "X-User-Language";

  /** 用户设备唯一标识。用于设备追踪与多端识别。 */
  public static final String X_DISTINCT_ID = "X-Distinct-Id";

  /** 身份类型。用于区分公司用户、访客用户、ydsz用户等身份类型。 */
  public static final String X_IDENTITY_TYPE = "X-Identity-Type";

  /** 用户名 HTTP 头。由网关在认证后写入。 */
  public static final String X_USERNAME = "X-Username";

  /** 用户角色集合 HTTP 头（CSV）。逗号分隔的角色编码列表。 */
  public static final String X_USER_ROLES = "X-User-Roles";

  /** 用户权限集合 HTTP 头（CSV）。逗号分隔的权限编码列表。 */
  public static final String X_USER_PERMISSIONS = "X-User-Permissions";

  /** 服务类型。用于区分请求来源服务类型（WEB_SERVICE / APP_SERVICE）。 */
  public static final String X_SERVICE_TYPE = "X-Service-Type";
}
