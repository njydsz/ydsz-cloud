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
 * 钉钉 OAuth2 认证提供者。
 *
 * <p>实现钉钉扫码登录流程：
 *
 * <ol>
 *   <li>生成授权 URL（钉钉 OAuth2 授权）</li>
 *   <li>用 code 换取 access_token</li>
 *   <li>获取用户信息（openid、nick、avatar、email）</li>
 * </ol>
 *
 * <p><b>钉钉 OAuth2 文档：</b>
 * <a href="https://open.dingtalk.com/document/orgapp/server-connect-to-get-user-identity-information">
 * 钉钉接入文档</a>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DingTalkAuthProvider extends AbstractSocialAuthProvider {

  /** 钉钉平台标识 */
  private static final String PLATFORM = "DINGTALK";

  /** 钉钉扫码登录授权端点（可通过 ydsz.userinfo.social.providers.dingtalk.authorize-url 覆盖） */
  private static final String DEFAULT_AUTHORIZE_URL = "https://login.dingtalk.com/oauth2/auth";

  /** 钉钉令牌端点（可通过 ydsz.userinfo.social.providers.dingtalk.access-token-url 覆盖） */
  private static final String DEFAULT_ACCESS_TOKEN_URL = "https://api.dingtalk.com/1.0.0/oauth2/userAccessToken";

  /** 钉钉用户信息端点（可通过 ydsz.userinfo.social.providers.dingtalk.user-info-url 覆盖） */
  private static final String DEFAULT_USER_INFO_URL = "https://api.dingtalk.com/1.0.0/contact/users";

  /** 默认令牌过期时间（秒） */
  private static final long DEFAULT_EXPIRE_IN = 7200L;

  /**
   * 构造钉钉认证提供者。
   *
   * @param socialAuthProperties 社交认证配置
   * @param httpClient HTTP 客户端
   */
  public DingTalkAuthProvider(SocialAuthProperties socialAuthProperties,
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
      throw new SocialAuthException("钉钉配置未找到");
    }

    String clientId = config.getAppId();
    String scope = config.getScope() != null ? config.getScope() : "openid";

    String authorizeUrl = config.getOrDefaultAuthorizeUrl(DEFAULT_AUTHORIZE_URL);

    String url = authorizeUrl
        + "?prompt=consent"
        + "&client_id=" + urlEncode(clientId)
        + "&redirect_uri=" + urlEncode(redirectUri)
        + "&scope=" + urlEncode(scope)
        + "&state=" + urlEncode(state)
        + "&response_type=code";

    log.debug("钉钉授权 URL 已生成: clientId={}", clientId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException("钉钉配置未找到");
    }

    String tokenUrl = config.getOrDefaultAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL);

    Map<String, String> tokenParams = new HashMap<>();
    tokenParams.put("clientId", config.getAppId());
    tokenParams.put("clientSecret", config.getAppSecret());
    tokenParams.put("code", code);
    tokenParams.put("grantType", "authorization_code");

    Map<String, Object> tokenResponse = httpClient.postJsonForMap(tokenUrl, tokenParams);

    String accessToken = getStr(tokenResponse, "accessToken");
    if (accessToken == null || accessToken.isBlank()) {
      throw new SocialAuthException("钉钉获取 access_token 失败");
    }

    Long expireIn = getLong(tokenResponse, "expireIn", DEFAULT_EXPIRE_IN);
    String unionId = getStr(tokenResponse, "unionId");

    return new SocialAccessToken(accessToken, null, expireIn, unionId, unionId);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException("钉钉配置未找到");
    }

    String userInfoUrl = config.getOrDefaultUserInfoUrl(DEFAULT_USER_INFO_URL);
    String url = userInfoUrl + "/" + token.openId();
    Map<String, Object> userResponse = httpClient.getForMap(url, token.accessToken(), null);

    String nick = getStr(userResponse, "nick");
    String avatar = getStr(userResponse, "avatarUrl");
    String email = getStr(userResponse, "email");
    String unionId = getStr(userResponse, "unionId");

    return new SocialUserInfo(token.openId(), unionId, nick, avatar, email, PLATFORM);
  }
}
