package com.njydsz.common.exception.code;

import lombok.Getter;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 安全模块错误码静态引用（编译时安全访问，26.09.19 新增）。
 *
 * <p>覆盖认证异常（A02xxx）、权限异常（A03xxx）和安全异常（C01xxx）。通过静态字段 {@link #UNAUTHORIZED}
 * 安全引用，获得 IDE 自动补全和类型检查。
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * throw BusinessException.of(SecurityErrorCode.UNAUTHORIZED);
 * throw new BusinessException(SecurityErrorCode.PERMISSION_DENIED, cause);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Getter
public enum SecurityErrorCode implements ResultCode {

  // ======================== A02 认证异常 ========================

  /** 未授权 */
  UNAUTHORIZED(SecurityExceptionCode.UNAUTHORIZED),
  /** 未登录 */
  NOT_LOGGED_IN(SecurityExceptionCode.NOT_LOGGED_IN),
  /** 会话过期 */
  SESSION_EXPIRED(SecurityExceptionCode.SESSION_EXPIRED),
  /** 认证失败 */
  AUTHENTICATION_FAILED(SecurityExceptionCode.AUTHENTICATION_FAILED),
  /** 账号已禁用 */
  ACCOUNT_DISABLED(SecurityExceptionCode.ACCOUNT_DISABLED),
  /** 账号在其他地方登录 */
  ACCOUNT_LOGGED_ELSEWHERE(SecurityExceptionCode.ACCOUNT_LOGGED_ELSEWHERE),

  // ======================== A03 权限异常 ========================

  /** 禁止访问 */
  FORBIDDEN(SecurityExceptionCode.FORBIDDEN),
  /** 权限不足 */
  INSUFFICIENT_PERMISSIONS(SecurityExceptionCode.INSUFFICIENT_PERMISSIONS),
  /** 访问被拒绝 */
  ACCESS_DENIED(SecurityExceptionCode.ACCESS_DENIED),
  /** 角色不匹配 */
  ROLE_MISMATCH(SecurityExceptionCode.ROLE_MISMATCH),

  // ======================== C01 安全异常 ========================

  /** 安全访问被拒绝 */
  SEC_ACCESS_DENIED(SecurityExceptionCode.SEC_ACCESS_DENIED),
  /** 需要认证 */
  AUTHENTICATION_REQUIRED(SecurityExceptionCode.AUTHENTICATION_REQUIRED),
  /** Token过期 */
  TOKEN_EXPIRED(SecurityExceptionCode.TOKEN_EXPIRED),
  /** 权限拒绝（通用） */
  PERMISSION_DENIED(SecurityExceptionCode.PERMISSION_DENIED),

  // ======================== C01 细分权限拒绝 ========================

  /** 菜单权限拒绝 */
  PERMISSION_DENIED_MENU(SecurityExceptionCode.PERMISSION_DENIED_MENU),
  /** 按钮权限拒绝 */
  PERMISSION_DENIED_BUTTON(SecurityExceptionCode.PERMISSION_DENIED_BUTTON),
  /** 接口权限拒绝 */
  PERMISSION_DENIED_API(SecurityExceptionCode.PERMISSION_DENIED_API),
  /** 数据权限拒绝 */
  PERMISSION_DENIED_DATA(SecurityExceptionCode.PERMISSION_DENIED_DATA),
  /** 列权限拒绝 */
  PERMISSION_DENIED_COLUMN(SecurityExceptionCode.PERMISSION_DENIED_COLUMN),

  // ======================== C01 密码安全 ========================

  /** 密码强度不足 */
  PASSWORD_TOO_WEAK(SecurityExceptionCode.PASSWORD_TOO_WEAK),
  /** 密码与历史密码重复 */
  PASSWORD_REUSED(SecurityExceptionCode.PASSWORD_REUSED),
  /** 内部签名校验失败 */
  INTERNAL_SIGNATURE_INVALID(SecurityExceptionCode.INTERNAL_SIGNATURE_INVALID);

  /** 委托的目标枚举常量 */
  private final SecurityExceptionCode delegate;

  /** 构造安全错误码引用 */
  SecurityErrorCode(SecurityExceptionCode delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getCode() {
    return delegate.getCode();
  }

  @Override
  public String getKey() {
    return delegate.getKey();
  }

  @Override
  public String getMsg() {
    return delegate.getKey();
  }

  /**
   * 获取 HTTP 状态码
   *
   * @return HTTP 状态码
   */
  public int httpStatus() {
    return delegate.getHttpStatus();
  }

  /**
   * 获取异常分类（统一为 SECURITY）
   *
   * @return {@link ExceptionCategory#SECURITY}
   */
  public ExceptionCategory category() {
    return ExceptionCategory.SECURITY;
  }

  /**
   * 获取异常级别
   *
   * @return 异常级别
   */
  public ExceptionLevel level() {
    return delegate.getLevel();
  }
}
