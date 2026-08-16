package com.njydsz.common.safe.alert;

/**
 * 安全事件类型枚举
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum SecurityEventType {

  /** XSS 攻击检测 */
  XSS_ATTACK,

  /** CSRF 攻击检测 */
  CSRF_ATTACK,

  /** 暴力破解检测 */
  BRUTE_FORCE,

  /** 非法访问检测 */
  ILLEGAL_ACCESS,

  /** 限流触发 */
  RATE_LIMIT_TRIGGERED,

  /** IP 自动封禁（安全事件聚合触发） */
  IP_AUTO_BLOCKED,

  /** API 签名验证失败 */
  SIGNATURE_INVALID,

  /** 密码强度不足 */
  WEAK_PASSWORD
}
