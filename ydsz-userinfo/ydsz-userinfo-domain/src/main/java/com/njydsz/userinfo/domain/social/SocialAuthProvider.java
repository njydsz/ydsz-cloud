package com.njydsz.userinfo.domain.social;

/**
 * 社交认证提供者接口（统一抽象层）。
 *
 * <p>屏蔽不同 OAuth2 平台的差异，提供统一的认证流程。各平台（微信、钉钉、企业微信、GitHub 等）
 * 通过实现本接口接入社交登录体系。
 *
 * <p><b>实现约定：</b>
 *
 * <ul>
 *   <li>实现类应为 Spring Bean，通过 {@link #getPlatform()} 作为唯一标识注入 Map</li>
 *   <li>所有方法抛出 {@link SocialAuthException} 表示认证失败，由上层统一捕获处理</li>
 *   <li>线程安全：实现类应为无状态，可多线程并发调用</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SocialAuthProvider {

  /**
   * 获取平台标识。
   *
   * <p>返回值与 {@code SocialAccount.getPlatform()} 对应，如 {@code WECHAT}、{@code DINGTALK}、
   * {@code ENTERPRISE_WECHAT}、{@code GITHUB}。全局唯一，用作 Map 键和配置键。
   *
   * @return 平台标识字符串（大写枚举风格）
   */
  String getPlatform();

  /**
   * 生成授权 URL。
   *
   * <p>拼接平台 OAuth2 授权端点 URL，包含 appId、redirectUri、state、scope 等参数。
   * 前端通过此 URL 跳转至平台授权页面。
   *
   * @param state 防 CSRF 的随机状态码，由原样回调后校验（不可为空）
   * @param redirectUri 授权完成后重定向回应用的回调地址（不可为空）
   * @return 完整的平台授权页面 URL
   */
  String authorize(String state, String redirectUri);

  /**
   * 用授权码换取访问令牌。
   *
   * <p>使用平台 OAuth2 token 端点，以授权码交换 access_token 和 refresh_token。
   *
   * @param code 授权码（平台回调时携带，一次性有效）
   * @param redirectUri 回调地址，必须与 {@link #authorize} 时传入的一致
   * @return 访问令牌值对象
   * @throws SocialAuthException 换取失败时抛出
   */
  SocialAccessToken exchangeToken(String code, String redirectUri);

  /**
   * 获取社交用户信息。
   *
   * <p>使用访问令牌查询平台用户信息端点，获取用户唯一标识、昵称、头像等信息。
   *
   * @param token 访问令牌（由 {@link #exchangeToken} 获取）
   * @return 社交用户信息值对象
   * @throws SocialAuthException 获取失败时抛出
   */
  SocialUserInfo getUserInfo(SocialAccessToken token);
}
