package com.njydsz.userinfo.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * SAML 2.0 配置属性
 *
 * <p>封装 ydsz.userinfo.saml 前缀下的配置项，用于控制 SAML Service Provider 的行为。
 *
 * <p><b>SAML 2.0 SP 角色职责：</b>
 *
 * <ul>
 *   <li>生成 SP Metadata（Entity ID、AssertionConsumerService URL、公钥证书）
 *   <li>接收并验证 IdP 签发的 SAML Assertion
 *   <li>从中提取用户身份（NameID / Attribute）并建立本地会话
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.saml")
public class SamlProperties {

  /** 是否启用 SAML 2.0 SP 端点 */
  private boolean enabled = false;

  /**
   * SP Entity ID
   *
   * <p>Service Provider 的唯一标识 URI，将在 Metadata 中作为 EntityDescriptor 的 entityID 发布。
   * 建议格式：https://{domain}/saml/metadata
   */
  private String entityId = "https://userinfo.ydsz.com/saml";

  /**
   * Assertion Consumer Service (ACS) URL
   *
   * <p>IdP 通过 HTTP POST 将 SAML Response 发送到此端点。
   */
  private String acsUrl = "https://userinfo.ydsz.com/saml/acs";

  /** IdP Entity ID（对端身份提供者的唯一标识） */
  private String idpEntityId;

  /** IdP SSO 端点 URL（SP 重定向用户至此发起 SSO） */
  private String idpSsoUrl;

  /** IdP 公钥证书（用于验证 SAML Response 签名，PEM 格式） */
  private String idpCertificate;

  /** SP 签名私钥 PEM 格式（用于签名 AuthnRequest，可选） */
  private String spPrivateKey;

  /** SP 公钥证书 PEM 格式（发布到 Metadata，供 IdP 验证签名） */
  private String spCertificate;

  /** SAML Response 最大有效时间（秒），默认 300 秒防重放 */
  private long maxClockSkewSeconds = 300;

  /** 是否要求 IdP 对 Response 签名 */
  private boolean wantAssertionsSigned = true;
}
