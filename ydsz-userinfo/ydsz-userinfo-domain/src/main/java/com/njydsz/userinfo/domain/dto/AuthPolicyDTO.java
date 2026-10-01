package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 认证策略统一 DTO（P3-1 多租户认证域隔离）。
 *
 * <p>同时用于创建和更新场景：创建时 {@code tenantId} 可不传（为空表示全局默认策略），
 * 更新时 {@code tenantId} 必填。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class AuthPolicyDTO {

  /** 租户 ID（为空表示全局默认策略） */
  @Xss(message = "{userinfo.param.xss}")
  private String tenantId;

  /** 策略名称 */
  @Xss(message = "{userinfo.param.xss}")
  @Size(max = 128, message = "{userinfo.error.max_length}")
  private String name;

  /** 密码最小长度（≥ 6） */
  private Integer passwordMinLength;

  /** 密码必须包含大写字母 */
  private Boolean isPasswordRequireUppercase;

  /** 密码必须包含数字 */
  private Boolean isPasswordRequireDigit;

  /** 密码是否启用双因素认证 */
  private Boolean isMfaEnabled;

  /** 登录是否启用图形验证码 */
  private Boolean isCaptchaEnabled;

  /** 允许的身份提供者类型（逗号分隔，如 "LDAP,SAML,OAUTH2"） */
  @Xss(message = "{userinfo.param.xss}")
  private String allowedIdentityProviders;

  /** 最大会话数（每个用户同时在线的最大会话数） */
  private Integer maxSessionsPerUser;

  /** 会话超时时间（秒） */
  private Integer sessionTimeoutSeconds;

  /** 备注说明 */
  @Xss(message = "{userinfo.param.xss}")
  @Size(max = 500, message = "{userinfo.error.max_length}")
  private String remark;
}
