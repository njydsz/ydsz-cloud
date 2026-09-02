package com.njydsz.userinfo.domain.auth;

import java.util.Map;

import com.njydsz.userinfo.domain.enums.IdentityProviderType;

/**
 * 用户身份提供者接口（P2-2 多账号认证体系抽象）。
 *
 * <p>统一抽象不同认证源的身份验证逻辑，支持：
 *
 * <ul>
 *   <li><b>LOCAL</b> — 本地数据库用户（标准 BCrypt 密码校验）</li>
 *   <li><b>LDAP</b> — LDAP/AD 目录服务认证</li>
 *   <li><b>SAML</b> — SAML 2.0 IdP 认证</li>
 *   <li><b>SOCIAL</b> — 社会化登录（钉钉/企微/飞书）</li>
 *   <li><b>OAUTH2</b> — 外部 OAuth2/OIDC Provider</li>
 * </ul>
 *
 * <p>每个认证源实现此接口，由 {@link UserIdentityProviderFactory} 根据用户类型路由到对应实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface UserIdentityProvider {

  /**
   * 获取此 Provider 支持的认证类型。
   *
   * @return 认证类型枚举
   */
  IdentityProviderType getType();

  /**
   * 验证用户身份。
   *
   * @param username 用户名（或邮箱/手机号，取决于认证源）
   * @param credentials 凭证（密码/授权码/断言等，取决于认证源）
   * @return 认证成功返回用户属性 Map；失败返回 null
   */
  Map<String, String> authenticate(String username, String credentials);

  /**
   * 判断此 Provider 是否支持指定用户。
   *
   * <p>用于工厂方法路由：根据用户记录中的 identityProvider 字段判断由哪个 Provider 处理密码校验、
   * 会话刷新等操作。
   *
   * @param userIdentityProvider 用户记录的认证源标识
   * @return true 表示此 Provider 处理该用户
   */
  boolean supports(String userIdentityProvider);
}
