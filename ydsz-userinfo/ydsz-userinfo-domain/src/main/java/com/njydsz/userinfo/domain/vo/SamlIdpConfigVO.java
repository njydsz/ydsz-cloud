package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * SAML 身份提供者配置视图出参（P2-1 查询返回值）。
 *
 * @author ydsz-team
 * @since 1.0.0
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
