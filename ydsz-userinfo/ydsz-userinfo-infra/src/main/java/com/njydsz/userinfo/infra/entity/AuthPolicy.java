package com.njydsz.userinfo.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 认证策略实体（P3-1 多租户认证域隔离）。
 *
 * <p>对应数据库表 {@code ydsz_auth_policy}，存储租户级认证策略配置。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>{@code uk_tenant_id} — 租户 ID 唯一索引（每个租户最多一条策略）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_auth_policy")
@SuppressWarnings("unchecked") // @SuperBuilder 生成的代码会触发 unchecked 警告，无法在源码层面修复
public class AuthPolicy extends MpBaseEntity<String> {

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

  /** 是否启用双因素认证 */
  private Boolean mfaEnabled;

  /** 登录是否启用图形验证码 */
  private Boolean captchaEnabled;

  /** 允许的身份提供者类型（逗号分隔） */
  private String allowedIdentityProviders;

  /** 最大会话数 */
  private Integer maxSessionsPerUser;

  /** 会话超时时间（秒） */
  private Integer sessionTimeoutSeconds;

  /** 备注说明 */
  private String remark;
}
