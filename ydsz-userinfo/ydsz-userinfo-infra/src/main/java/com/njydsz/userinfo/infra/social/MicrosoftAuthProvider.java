package com.njydsz.userinfo.infra.social;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.config.SocialAuthProperties;
import com.njydsz.userinfo.domain.social.SocialAccessToken;
import com.njydsz.userinfo.domain.social.SocialAuthException;
import com.njydsz.userinfo.domain.social.SocialUserInfo;

/**
 * Microsoft OAuth2 认证提供者（Microsoft Identity Platform v2.0）。
 *
 * <p>实现 Microsoft 账号（个人/企业 Azure AD）授权登录流程：
 *
 * <ol>
 *   <li>生成授权 URL（Microsoft identity platform v2.0 授权端点）</li>
 *   <li>用 code 换取 access_token（支持 common/organizations/consumers 等租户）</li>
 *   <li>调用 Microsoft Graph API 获取用户信息（displayName、mail、id）</li>
 * </ol>
 *
 * <p><b>Microsoft OAuth2 文档：</b>
 * <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-auth-code-flow">
 * Microsoft identity platform</a>
 *
 * <p><b>特殊处理：</b>Microsoft 的 token 端点支持多租户 URL，如
 * {@code https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token}，
 * 通过配置中的 {@code scope} 字段替换 tenant 占位符（或使用 common 作为默认）。
 *
 * <p><b>配置要求：</b>需要在 Azure Portal 注册应用，获取 Application (client) ID +
 * Client secret，并配置 Redirect URI 为 Web 平台。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
public class MicrosoftAuthProvider extends AbstractSocialAuthProvider {

  /** 请求参数 Map 初始容量 */
  private static final int PARAMS_CAPACITY = 16;

  /** Microsoft 平台标识 */
  private static final String PLATFORM = "MICROSOFT";

  /** Microsoft 授权端点（可通过 ydsz.userinfo.social.providers.microsoft.authorize-url 覆盖） */
  private static final String DEFAULT_AUTHORIZE_URL =
      "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";

  /** Microsoft 令牌端点模板（可通过 ydsz.userinfo.social.providers.microsoft.access-token-url 覆盖） */
  private static final String DEFAULT_ACCESS_TOKEN_URL =
      "https://login.microsoftonline.com/common/oauth2/v2.0/token";

  /** Microsoft Graph API 用户信息端点 */
  private static final String DEFAULT_USER_INFO_URL = "https://graph.microsoft.com/v1.0/me";

  /** 默认令牌过期时间（秒） */
  private static final long DEFAULT_EXPIRE_IN = 3600L;

  /**
   * 构造 Microsoft 认证提供者。
   *
   * @param socialAuthProperties 社交认证配置
   * @param httpClient HTTP 客户端
   */
  public MicrosoftAuthProvider(SocialAuthProperties socialAuthProperties,
      JustAuthHttpClient httpClient) {
    super(socialAuthProperties, httpClient);
  }

  @Override
  public String getPlatform() {
    return PLATFORM;
  }

  @Override
  public String authorize(String state, String redirectUri) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "Microsoft 配置未找到");
    }

    String clientId = config.getAppId();
    String scope = config.getScope() != null ? config.getScope()
        : "openid email profile User.Read";
    String authorizeUrl = config.getOrDefaultAuthorizeUrl(DEFAULT_AUTHORIZE_URL);

    String url = authorizeUrl
        + "?client_id=" + urlEncode(clientId)
        + "&redirect_uri=" + urlEncode(redirectUri)
        + "&response_type=code"
        + "&scope=" + urlEncode(scope)
        + "&state=" + urlEncode(state)
        + "&response_mode=query";

    log.debug("Microsoft 授权 URL 已生成: clientId={}", clientId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "Microsoft 配置未找到");
    }

    String tokenUrl = config.getOrDefaultAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL);

    Map<String, String> tokenParams = new HashMap<>(PARAMS_CAPACITY);
    tokenParams.put("code", code);
    tokenParams.put("client_id", config.getAppId());
    tokenParams.put("client_secret", config.getAppSecret());
    tokenParams.put("redirect_uri", redirectUri);
    tokenParams.put("grant_type", "authorization_code");
    tokenParams.put("scope", "openid email profile User.Read");

    Map<String, Object> tokenResponse = httpClient.postFormForMap(tokenUrl, tokenParams);

    String error = getStr(tokenResponse, "error");
    if (error != null && !error.isBlank()) {
      String errorDescription = getStr(tokenResponse, "error_description");
      throw new SocialAuthException(PLATFORM,
          "Microsoft 获取 access_token 失败: " + errorDescription);
    }

    String accessToken = getStr(tokenResponse, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new SocialAuthException(PLATFORM, "Microsoft 获取 access_token 失败: 未返回 access_token");
    }

    Long expireIn = getLong(tokenResponse, "expires_in", DEFAULT_EXPIRE_IN);

    return new SocialAccessToken(accessToken, null, expireIn, null, null);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "Microsoft 配置未找到");
    }

    String userInfoUrl = config.getOrDefaultUserInfoUrl(DEFAULT_USER_INFO_URL);

    Map<String, Object> userResponse = httpClient.getForMap(userInfoUrl, token.accessToken(), null);

    String error = getStr(userResponse, "error");
    if (error != null && !error.isBlank()) {
      String errorMessage = getStr(userResponse, "message");
      throw new SocialAuthException(PLATFORM, "Microsoft 获取用户信息失败: " + errorMessage);
    }

    // Microsoft Graph: id 作为 openId，displayName 作为昵称，mail 作为邮箱
    String openId = getStr(userResponse, "id");
    String nickname = getStr(userResponse, "displayName");
    String email = getStr(userResponse, "mail");
    if (email == null) {
      email = getStr(userResponse, "userPrincipalName");
    }

    return new SocialUserInfo(openId, null, nickname, null, email, PLATFORM);
  }
}
