package com.njydsz.userinfo.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebAuthn/Passkey 配置属性
 *
 * <p>封装 ydsz.userinfo.webauthn 前缀下的配置项，用于控制 FIDO2 WebAuthn 的行为。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.webauthn")
public class WebAuthnProperties {

  /** 默认challengeTtlSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_CHALLENGE_TTL_SECONDS = 120;

  /** 默认maxCredentialsPerUser值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_CREDENTIALS_PER_USER = 5;

  /** 是否启用 WebAuthn/Passkey 无密码登录 */
  private boolean enabled = false;

  /**
   * 依赖方名称（Relying Party Name）
   *
   * <p>显示给用户的依赖方标识，如 "云顶上智"。
   */
  private String relyingPartyName = "YDSZ Cloud";

  /**
   * 依赖方 ID（Relying Party ID）
   *
   * <p>域名格式，不含协议和端口。如 "userinfo.ydsz.com"。
   * 必须与当前站点域名匹配（或为其父域）。
   */
  private String relyingPartyId = "userinfo.ydsz.com";

  /**
   * 认证来源（Origin）
   *
   * <p>浏览器 WebAPI 调用的来源 URL，必须与 RP ID 匹配。
   */
  private String origin = "https://userinfo.ydsz.com";

  /** 挑战码有效期（秒） */
  private long challengeTtlSeconds = DEFAULT_CHALLENGE_TTL_SECONDS;

  /** 是否允许用户注册多个凭证 */
  private boolean allowMultipleCredentials = true;

  /** 单用户最大凭证数 */
  private int maxCredentialsPerUser = DEFAULT_MAX_CREDENTIALS_PER_USER;
}
