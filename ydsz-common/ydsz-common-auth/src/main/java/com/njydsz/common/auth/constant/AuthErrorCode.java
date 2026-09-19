package com.njydsz.common.auth.constant;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * 认证模块业务异常码。
 *
 * <p>定义认证链路（Token 校验、用户状态、账号合法性）中的细粒度错误码， 用于替代 {@code RbacPermissionEvaluator} 中原有的硬编码字符串。
 *
 * <p>错误码格式：AUTH-BIZ-xxx，便于日志检索和问题定位。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
public enum AuthErrorCode implements ExceptionCode {

  /** 缺少访问令牌 */
  TOKEN_MISSING("AUTH-BIZ-001", "access_token_missing", "缺少访问令牌", 401),

  /** 访问令牌已过期，请重新登录 */
  TOKEN_EXPIRED("AUTH-BIZ-002", "access_token_expired", "访问令牌已过期，请重新登录", 401),

  /** 访问令牌无效 */
  TOKEN_INVALID("AUTH-BIZ-003", "access_token_invalid", "访问令牌无效", 401),

  /** 账号已被禁用 */
  ACCOUNT_DISABLED("AUTH-BIZ-004", "account_disabled", "账号已被禁用", 401),

  /** 账号已被锁定 */
  ACCOUNT_LOCKED("AUTH-BIZ-005", "account_locked", "账号已被锁定", 401),

  /** 用户不存在 */
  USER_NOT_FOUND("AUTH-BIZ-006", "user_not_found", "用户不存在", 401),

  /** API Key 无效或已撤销 */
  API_KEY_INVALID("AUTH-BIZ-007", "api_key_invalid", "API Key 无效或已撤销", 401),

  /** API Key 已过期 */
  API_KEY_EXPIRED("AUTH-BIZ-008", "api_key_expired", "API Key 已过期", 401),

  /** API Key 权限范围不足 */
  API_KEY_INSUFFICIENT_SCOPE("AUTH-BIZ-009", "api_key_insufficient_scope", "API Key 权限范围不足", 403),

  /** 验证码校验失败 */
  CAPTCHA_INVALID("AUTH-BIZ-010", "captcha_invalid", "验证码校验失败", 401),

  /** IP 登录频率超限 */
  IP_RATE_LIMITED("AUTH-BIZ-011", "ip_rate_limited", "该 IP 登录过于频繁，请稍后再试", 429);

  /** 异常错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** 默认兜底消息 */
  private final String msg;

  /** HTTP 状态码 */
  private final int httpStatus;

  AuthErrorCode(String code, String key, String msg, int httpStatus) {
    this.code = code;
    this.key = key;
    this.msg = msg;
    this.httpStatus = httpStatus;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public String getMsg() {
    return msg;
  }

  @Override
  public int getHttpStatus() {
    return httpStatus;
  }
}
