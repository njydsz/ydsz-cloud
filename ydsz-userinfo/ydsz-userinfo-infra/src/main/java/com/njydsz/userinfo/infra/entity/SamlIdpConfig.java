package com.njydsz.userinfo.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * SAML 身份提供者配置实体（P2-1 多租户 SAML）。
 *
 * <p>对应数据库表 {@code ydsz_saml_idp_config}，存储 SAML IdP 的元数据和证书信息。
 * 支持多租户隔离，不同租户可配置独立的 SAML IdP。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>{@code uk_entity_id} — Entity ID 唯一索引（Entity ID 是 SAML 协议中的唯一标识）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_saml_idp_config")
public class SamlIdpConfig extends MpBaseEntity<String> {

  /** IdP 显示名称（如 "企业微信 SAML"、"飞书 SAML"） */
  private String name;

  /** IdP Entity ID（SAML 协议中 IdP 的唯一标识 URI） */
  private String entityId;

  /** IdP SSO 端点 URL（SP 重定向用户至此发起 SSO） */
  private String ssoUrl;

  /** IdP 公钥证书（PEM 格式，用于验证 SAML Response 签名） */
  private String certificate;

  /** 用户邮箱对应的 SAML Attribute 名称（默认 "email"） */
  private String emailAttribute;

  /** 用户显示名称对应的 SAML Attribute 名称（默认 "displayName"） */
  private String displayNameAttribute;

  /** 状态：ENABLED / DISABLED */
  private String status;

  /** 排序权重（越小越靠前，用于前端展示排序） */
  private Integer sortOrder;

  /** 备注说明 */
  private String remark;
}
