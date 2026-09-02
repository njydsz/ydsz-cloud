package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 认证策略视图对象（P3-1 多租户认证域隔离）。
 *
 * <p>封装租户级认证策略配置，供管理端查询接口返回。每个租户最多一条策略，未配置时回退到全局默认策略。
 *
 * <p><b>关键字段说明：</b>
 *
 * <ul>
 *   <li>{@code tenantId} — 租户 ID（为空表示全局默认策略）</li>
 *   <li>{@code passwordMinLength} — 密码最小长度（>= 6）</li>
 *   <li>{@code passwordRequireUppercase} — 密码必须包含大写字母</li>
 *   <li>{@code passwordRequireDigit} — 密码必须包含数字</li>
 *   <li>{@code mfaEnabled} — 是否启用双因素认证</li>
 *   <li>{@code captchaEnabled} — 登录是否启用图形验证码</li>
 *   <li>{@code allowedIdentityProviders} — 允许的身份提供者类型（如 LOCAL,LDAP,SAML）</li>
 *   <li>{@code maxSessionsPerUser} — 单用户最大并发会话数</li>
 *   <li>{@code sessionTimeoutSeconds} — 会话超时时间（秒）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
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
