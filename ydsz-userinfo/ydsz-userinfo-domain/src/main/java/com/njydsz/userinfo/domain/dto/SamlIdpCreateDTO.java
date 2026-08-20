package com.njydsz.userinfo.domain.dto;

import lombok.Data;

/**
 * SAML 身份提供者配置创建 DTO（P2-1 CUD 入参）。
 *
 * <p>用于注册新的 SAML IdP，支持多租户隔离。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Data
public class SamlIdpCreateDTO {

  /** IdP 显示名称（如 "企业微信 SAML"、"飞书 SAML"） */
  private String name;

  /** IdP Entity ID（唯一标识） */
  private String entityId;

  /** IdP SSO 端点 URL */
  private String ssoUrl;

  /** IdP 公钥证书（PEM 格式，用于验证 SAML Response 签名） */
  private String certificate;

  /** 用户邮箱对应的 SAML Attribute 名称（默认 email） */
  private String emailAttribute;

  /** 用户显示名称对应的 SAML Attribute 名称（默认 displayName） */
  private String displayNameAttribute;

  /** 状态：ENABLED / DISABLED */
  private String status;

  /** 排序权重 */
  private Integer sortOrder;

  /** 备注说明 */
  private String remark;
}
