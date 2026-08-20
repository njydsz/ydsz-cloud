package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

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
  @NotBlank(message = "IdP 显示名称不能为空")
  @Size(max = 128, message = "IdP 显示名称长度不能超过 128 个字符")
  @Xss(message = "IdP 显示名称包含非法内容")
  private String name;

  /** IdP Entity ID（唯一标识） */
  @NotBlank(message = "Entity ID 不能为空")
  @Size(max = 255, message = "Entity ID 长度不能超过 255 个字符")
  @Xss(message = "Entity ID 包含非法内容")
  private String entityId;

  /** IdP SSO 端点 URL */
  @NotBlank(message = "SSO 端点 URL 不能为空")
  @Size(max = 512, message = "SSO 端点 URL 长度不能超过 512 个字符")
  @Xss(message = "SSO 端点 URL 包含非法内容")
  private String ssoUrl;

  /** IdP 公钥证书（PEM 格式，用于验证 SAML Response 签名） */
  @Xss(message = "公钥证书包含非法内容")
  private String certificate;

  /** 用户邮箱对应的 SAML Attribute 名称（默认 email） */
  @Size(max = 64, message = "邮箱属性名长度不能超过 64 个字符")
  @Xss(message = "邮箱属性名包含非法内容")
  private String emailAttribute;

  /** 用户显示名称对应的 SAML Attribute 名称（默认 displayName） */
  @Size(max = 64, message = "显示名称属性名长度不能超过 64 个字符")
  @Xss(message = "显示名称属性名包含非法内容")
  private String displayNameAttribute;

  /** 状态：ENABLED / DISABLED */
  @Size(max = 20, message = "状态长度不能超过 20 个字符")
  @Xss(message = "状态包含非法内容")
  private String status;

  /** 排序权重 */
  private Integer sortOrder;

  /** 备注说明 */
  @Size(max = 500, message = "备注长度不能超过 500 个字符")
  @Xss(message = "备注包含非法内容")
  private String remark;
}
