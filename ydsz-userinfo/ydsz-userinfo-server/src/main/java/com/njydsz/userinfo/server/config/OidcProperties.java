package com.njydsz.userinfo.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC（OpenID Connect）配置属性
 *
 * <p>封装 ydsz.userinfo.oidc 前缀下的配置项，用于控制 OIDC Provider 的行为。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.oidc")
public class OidcProperties {

  /** 默认idTokenExpireSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_ID_TOKEN_EXPIRE_SECONDS = 600;

  /** 是否启用 OIDC 协议支持 */
  private boolean enabled = true;

  /**
   * Issuer 标识（Issuer Identifier）
   *
   * <p>OIDC Provider 的唯一标识 URL，必须以 https:// 开头。ID Token 的 iss 声明将使用此值。
   * 生产环境必须使用 HTTPS。
   */
  private String issuer = "https://userinfo.ydsz.com";

  /** 是否在校验 ID Token 时强制验证 nonce 声明 */
  private boolean nonceRequired = false;

  /**
   * ID Token 有效期（秒）
   *
   * <p>默认 600 秒（10 分钟），符合 OIDC Core 1.0 对 ID Token 短效期的建议。
   */
  private long idTokenExpireSeconds = DEFAULT_ID_TOKEN_EXPIRE_SECONDS;

  /** 是否在授权响应中自动返回 ID Token（scope 含 openid 时） */
  private boolean autoIssueIdToken = true;
}
