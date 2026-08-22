package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 认证策略视图出参（P3-1 查询返回值）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AuthPolicyVO {

  /** 策略 ID */
  private String id;

  /** 租户 ID（为空表示全局默认策略） */
  private String tenantId;

  /** 策略名称 */
  private String name;

  /** 密码最小长度 */
  private Integer passwordMinLength;

  /** 密码必须包含大写字母 */
  private Boolean passwordRequireUppercase;

  /** 密码必须包含数字 */
  private Boolean passwordRequireDigit;

  /** 是否启用双因素认证 */
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

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
