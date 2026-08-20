package com.njydsz.userinfo.domain.dto;

import lombok.Data;

/**
 * 认证策略更新 DTO（P3-1 多租户认证域隔离）。
 *
 * <p>用于修改已有认证策略，所有字段均可选（null 表示不修改）。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Data
public class AuthPolicyUpdateDTO {

  /** 策略名称 */
  private String name;

  /** 密码最小长度（≥ 6） */
  private Integer passwordMinLength;

  /** 密码必须包含大写字母 */
  private Boolean passwordRequireUppercase;

  /** 密码必须包含数字 */
  private Boolean passwordRequireDigit;

  /** 密码是否启用双因素认证 */
  private Boolean mfaEnabled;

  /** 登录是否启用图形验证码 */
  private Boolean captchaEnabled;

  /** 允许的身份提供者类型 */
  private String allowedIdentityProviders;

  /** 最大会话数 */
  private Integer maxSessionsPerUser;

  /** 会话超时时间（秒） */
  private Integer sessionTimeoutSeconds;

  /** 备注说明 */
  private String remark;
}
