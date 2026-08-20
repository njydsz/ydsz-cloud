package com.njydsz.userinfo.domain.dto;

import lombok.Data;

/**
 * 认证策略创建 DTO（P3-1 多租户认证域隔离）。
 *
 * <p>用于创建租户级认证策略，未配置的策略项继承全局默认值。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Data
public class AuthPolicyCreateDTO {

  /** 租户 ID（为空表示全局默认策略） */
  private String tenantId;

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

  /** 允许的身份提供者类型（逗号分隔，如 "LDAP,SAML,OAUTH2"） */
  private String allowedIdentityProviders;

  /** 最大会话数（每个用户同时在线的最大会话数） */
  private Integer maxSessionsPerUser;

  /** 会话超时时间（秒） */
  private Integer sessionTimeoutSeconds;

  /** 备注说明 */
  private String remark;
}
