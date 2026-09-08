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
 * Google OAuth2 认证提供者。
 *
 * <p>实现 Google 网页授权登录流程：
 *
 * <ol>
 *   <li>生成授权 URL（Google OAuth2 授权码模式 + OIDC）</li>
 *   <li>用 code 换取 access_token（Google token 端点返回 access_token + id_token）</li>
 *   <li>（可选）从 id_token 解析用户信息，或调用 userinfo 端点</li>
 * </ol>
 *
 * <p><b>Google OAuth2 文档：</b>
 * <a href="https://developers.google.com/identity/protocols/oauth2/openid-connect">
 * Google OpenID Connect</a>
 *
 * <p><b>特殊处理：</b>Google 的 access_token 接口返回标准 JSON（Content-Type: application/json），
 * 使用 application/x-www-form-urlencoded POST 方式。
 *
 * <p><b>配置要求：</b>需要在 Google Cloud Console 创建 OAuth2 凭据，
 * 启用 Google+ API 或 People API，并设置已获授权的重定向 URI。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
public class GoogleAuthProvider extends AbstractSocialAuthProvider {

  /** 请求参数 Map 初始容量 */
  private static final int PARAMS_CAPACITY = 16;

  /** Google 平台标识 */
  private static final String PLATFORM = "GOOGLE";

  /** Google 授权端点（可通过 ydsz.userinfo.social.providers.google.authorize-url 覆盖） */
  private static final String DEFAULT_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";

  /** Google 令牌端点（可通过 ydsz.userinfo.social.providers.google.access-token-url 覆盖） */
  private static final String DEFAULT_ACCESS_TOKEN_URL = "https://oauth2.googleapis.com/token";

  /** Google 用户信息端点（可通过 ydsz.userinfo.social.providers.google.user-info-url 覆盖） */
  private static final String DEFAULT_USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

  /** 默认令牌过期时间（秒） */
  private static final long DEFAULT_EXPIRE_IN = 3600L;

  /**
   * 构造 Google 认证提供者。
   *
   * @param socialAuthProperties 社交认证配置
   * @param httpClient HTTP 客户端
   */
  public GoogleAuthProvider(SocialAuthProperties socialAuthProperties,
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
      throw new SocialAuthException(PLATFORM, "Google 配置未找到");
    }

    String clientId = config.getAppId();
    String scope = config.getScope() != null ? config.getScope() : "openid email profile";
    String authorizeUrl = config.getOrDefaultAuthorizeUrl(DEFAULT_AUTHORIZE_URL);

    String url = authorizeUrl
        + "?client_id=" + urlEncode(clientId)
        + "&redirect_uri=" + urlEncode(redirectUri)
        + "&response_type=code"
        + "&scope=" + urlEncode(scope)
        + "&state=" + urlEncode(state)
        + "&access_type=offline"
        + "&prompt=consent";

    log.debug("Google 授权 URL 已生成: clientId={}", clientId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "Google 配置未找到");
    }

    String tokenUrl = config.getOrDefaultAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL);

    Map<String, String> tokenParams = new HashMap<>(PARAMS_CAPACITY);
    tokenParams.put("code", code);
    tokenParams.put("client_id", config.getAppId());
    tokenParams.put("client_secret", config.getAppSecret());
    tokenParams.put("redirect_uri", redirectUri);
    tokenParams.put("grant_type", "authorization_code");

    Map<String, Object> tokenResponse = httpClient.postFormForMap(tokenUrl, tokenParams);

    String error = getStr(tokenResponse, "error");
    if (error != null && !error.isBlank()) {
      String errorDescription = getStr(tokenResponse, "error_description");
      throw new SocialAuthException(PLATFORM, "Google 获取 access_token 失败: " + errorDescription);
    }

    String accessToken = getStr(tokenResponse, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new SocialAuthException(PLATFORM, "Google 获取 access_token 失败: 未返回 access_token");
    }

    Long expireIn = getLong(tokenResponse, "expires_in", DEFAULT_EXPIRE_IN);
    // Google 的 openId 可从 id_token 中解析，这里用 email 作为备用
    String idToken = getStr(tokenResponse, "id_token");

    return new SocialAccessToken(accessToken, null, expireIn, null, idToken);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "Google 配置未找到");
    }

    String userInfoUrl = config.getOrDefaultUserInfoUrl(DEFAULT_USER_INFO_URL);

    Map<String, Object> userResponse = httpClient.getForMap(userInfoUrl, token.accessToken(), null);

    String error = getStr(userResponse, "error");
    if (error != null && !error.isBlank()) {
      throw new SocialAuthException(PLATFORM, "Google 获取用户信息失败: " + error);
    }

    // Google 的 sub 字段作为 openId
    String openId = getStr(userResponse, "id");
    String nickname = getStr(userResponse, "name");
    String avatar = getStr(userResponse, "picture");
    String email = getStr(userResponse, "email");

    return new SocialUserInfo(openId, token.unionId(), nickname, avatar, email, PLATFORM);
  }
}
