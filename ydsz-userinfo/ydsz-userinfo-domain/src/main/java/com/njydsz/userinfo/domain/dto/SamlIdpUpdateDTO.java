package com.njydsz.userinfo.domain.dto;

import lombok.Data;

/**
 * SAML 身份提供者配置更新 DTO（P2-1 CUD 入参）。
 *
 * <p>用于修改已有 SAML IdP 配置，所有字段均可选（null 表示不修改）。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Data
public class SamlIdpUpdateDTO {

  /** IdP 显示名称 */
  private String name;

  /** IdP SSO 端点 URL */
  private String ssoUrl;

  /** IdP 公钥证书（PEM 格式） */
  private String certificate;

  /** 用户邮箱对应的 SAML Attribute 名称 */
  private String emailAttribute;

  /** 用户显示名称对应的 SAML Attribute 名称 */
  private String displayNameAttribute;

  /** 状态：ENABLED / DISABLED */
  private String status;

  /** 排序权重 */
  private Integer sortOrder;

  /** 备注说明 */
  private String remark;
}
