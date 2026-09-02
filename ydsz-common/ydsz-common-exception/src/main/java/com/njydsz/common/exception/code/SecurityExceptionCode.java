package com.njydsz.common.exception.code;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 安全模块异常码。
 *
 * <p>认证异常（A02xxx）、权限异常（A03xxx）和安全异常（C01xxx）。 覆盖身份认证、会话管理、权限校验、Token 安全、CSRF 防护等安全场景。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CoreExceptionCode
 * @see RateLimitExceptionCode
 */
@Getter
@YdszExceptionCode(module = "security", description = "安全模块认证授权异常码")
public enum SecurityExceptionCode implements ExceptionCode {

  // ==================== A02 认证异常 ====================

  /**
   * 未授权（原 ResponseCode.UNAUTHORIZED 100401）
   *
   * @return 处理结果
   */
  UNAUTHORIZED("A02051", "unauthorized", 401),
  /**
   * 未登录
   *
   * @return 处理结果
   */
  NOT_LOGGED_IN("A02052", "not.logged.in", 401),
  /**
   * 会话过期
   *
   * @return 处理结果
   */
  SESSION_EXPIRED("A02053", "session.expired", 401),
  /**
   * 认证失败
   *
   * @return 处理结果
   */
  AUTHENTICATION_FAILED("A02054", "authentication.failed", 401),
  /**
   * 账号已禁用
   *
   * @return 处理结果
   */
  ACCOUNT_DISABLED("A02055", "account.disabled", 401),
  /**
   * 账号在其他地方登录
   *
   * @return 处理结果
   */
  ACCOUNT_LOGGED_ELSEWHERE("A02056", "account.logged.elsewhere", 401),

  // ==================== A03 权限异常 ====================

  /**
   * 禁止访问（原 ResponseCode.FORBIDDEN 100403）
   *
   * @return 处理结果
   */
  FORBIDDEN("A03051", "forbidden", 403),
  /**
   * 权限不足
   *
   * @return 处理结果
   */
  INSUFFICIENT_PERMISSIONS("A03052", "insufficient.permissions", 403),
  /**
   * 访问被拒绝
   *
   * @return 处理结果
   */
  ACCESS_DENIED("A03053", "access.denied", 403),
  /**
   * 角色不匹配
   *
   * @return 处理结果
   */
  ROLE_MISMATCH("A03054", "role.mismatch", 403),

  // ==================== C01 安全异常 ====================

  /**
   * 安全访问被拒绝
   *
   * @return 处理结果
   */
  SEC_ACCESS_DENIED("C01051", "security.access.denied", 403),
  /**
   * 需要认证
   *
   * @return 处理结果
   */
  AUTHENTICATION_REQUIRED("C01052", "security.authentication.required", 401),
  /**
   * Token过期
   *
   * @return 处理结果
   */
  TOKEN_EXPIRED("C01053", "security.token.expired", 401),
  /**
   * 权限拒绝（通用）
   *
   * @return 处理结果
   */
  PERMISSION_DENIED("C01054", "security.permission.denied", 403),

  // ==================== C01 细分权限拒绝（按权限类型） ====================

  /**
   * 菜单权限拒绝
   *
   * @return 处理结果
   */
  PERMISSION_DENIED_MENU("C01061", "security.permission.denied.menu", 403),
  /**
   * 按钮权限拒绝
   *
   * @return 处理结果
   */
  PERMISSION_DENIED_BUTTON("C01062", "security.permission.denied.button", 403),
  /**
   * 接口权限拒绝
   *
   * @return 处理结果
   */
  PERMISSION_DENIED_API("C01063", "security.permission.denied.api", 403),
  /**
   * 数据权限拒绝
   *
   * @return 处理结果
   */
  PERMISSION_DENIED_DATA("C01064", "security.permission.denied.data", 403),
  /**
   * 列权限拒绝
   *
   * @return 处理结果
   */
  PERMISSION_DENIED_COLUMN("C01065", "security.permission.denied.column", 403),

  // ==================== C01 密码安全 ====================

  /**
   * 密码强度不足（P0-1：注册/修改密码时强度校验失败）
   *
   * @return 处理结果
   */
  PASSWORD_TOO_WEAK("C01071", "security.password.too.weak", 400),
  /**
   * 密码与历史密码重复（P0-1：修改密码时与最近 N 条历史密码重复）
   *
   * @return 处理结果
   */
  PASSWORD_REUSED("C01072", "security.password.reused", 400),
  /**
   * 内部签名校验失败（P0-3：网关 X-Internal-Sig 下游验签不通过）
   *
   * @return 处理结果
   */
  INTERNAL_SIGNATURE_INVALID("C01081", "security.internal.signature.invalid", 403);

  // ==================== 字段定义 ====================

  /** 异常错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  SecurityExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }

  @Override
  public int getHttpStatus() {
    return httpStatus;
  }
}
