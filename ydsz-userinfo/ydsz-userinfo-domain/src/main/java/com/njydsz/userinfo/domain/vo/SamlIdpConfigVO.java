package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * SAML 2.0 身份提供者（IdP）配置视图对象（P2-1）。
 *
 * <p>展示已配置的外部 SAML IdP 信息，供管理端 IdP 配置管理界面展示和编辑。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code name} — IdP 显示名称（如"公司 ADFS"、"Okta"）</li>
 *   <li>{@code entityId} — IdP Entity ID（唯一标识 URI）</li>
 *   <li>{@code ssoUrl} — IdP SSO 端点 URL（SP 重定向用户至此发起 SSO）</li>
 *   <li>{@code certificate} — IdP 公钥证书（PEM 格式，用于验证 SAML Response 签名）</li>
 *   <li>{@code emailAttribute} — SAML Assertion 中邮箱属性名（如 emailAddress）</li>
 *   <li>{@code displayNameAttribute} — SAML Assertion 中显示名称属性名（如 displayName）</li>
 *   <li>{@code status} — 状态（ENABLED/DISABLED），禁用后该 IdP 不可登录</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SamlIdpConfigVO {

  /** 配置 ID */
  private String id;

  /** IdP 显示名称 */
  private String name;

  /** IdP Entity ID */
  private String entityId;

  /** IdP SSO 端点 URL */
  private String ssoUrl;

  /** IdP 公钥证书（PEM 格式，用于验证 SAML Response 签名） */
  private String certificate;

  /** 邮箱属性名 */
  private String emailAttribute;

  /** 显示名称属性名 */
  private String displayNameAttribute;

  /** 状态：ENABLED / DISABLED */
  private String status;

  /** 排序权重 */
  private Integer sortOrder;

  /** 备注说明 */
  private String remark;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 创建者用户 ID */
  private String createdBy;
}
