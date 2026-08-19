package com.njydsz.userinfo.domain.enums;

/**
 * 身份提供者类型枚举（P2-2 多账号认证体系）。
 *
 * <p>定义系统支持的用户认证源类型，用于：
 *
 * <ul>
 *   <li>用户标识（标记用户来源于哪个认证源）</li>
 *   <li>认证路由（根据类型选择对应的 {@link com.njydsz.userinfo.domain.auth.UserIdentityProvider} 实现）</li>
 *   <li>差异化策略（不同来源用户可使用不同的密码策略/MFA 策略/会话策略）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.24.0
 */
public enum IdentityProviderType {

  /**
   * 本地用户（系统内置 BCrypt 密码校验）。
   *
   * <p>标准用户类型，密码存储在本地 ydsz_user_account 表，使用 BCrypt 哈希。
   */
  LOCAL("local", "本地账号"),

  /**
   * LDAP/AD 目录服务用户。
   *
   * <p>用户密码存储在外部 LDAP/AD 服务器，认证通过 LDAP bind 操作完成。
   * 本地仅存储用户属性和同步状态。
   */
  LDAP("ldap", "LDAP 账号"),

  /**
   * SAML 2.0 外部 IdP 用户。
   *
   * <p>用户通过 SAML 2.0 协议在外部 IdP 完成认证，本地通过 NameID 关联用户身份。
   */
  SAML("saml", "SAML 账号"),

  /**
   * 社会化登录用户。
   *
   * <p>通过钉钉/企业微信/飞书等社会化平台 OAuth2 完成认证，本地存储平台 openId 关联。
   */
  SOCIAL("social", "社会化账号"),

  /**
   * 外部 OAuth2/OIDC Provider 用户。
   *
   * <p>通过外部 OAuth2/OIDC 认证（如 Keycloak、Auth0）完成身份验证。
   */
  OAUTH2("oauth2", "OAuth2 账号");

  /** 类型标识（存储在用户记录中） */
  private final String code;

  /** 显示名称 */
  private final String displayName;

  IdentityProviderType(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  /**
   * 根据 code 值解析枚举。
   *
   * @param code 类型标识
   * @return 对应枚举；未找到返回 LOCAL（默认值）
   */
  public static IdentityProviderType fromCode(String code) {
    if (code != null) {
      for (IdentityProviderType type : values()) {
        if (type.code.equals(code)) {
          return type;
        }
      }
    }
    return LOCAL;
  }
}
