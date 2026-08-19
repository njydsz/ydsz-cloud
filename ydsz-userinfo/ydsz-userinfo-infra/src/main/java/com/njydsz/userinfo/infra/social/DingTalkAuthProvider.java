package com.njydsz.userinfo.infra.social;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.social.SocialAccessToken;
import com.njydsz.userinfo.domain.social.SocialAuthException;
import com.njydsz.userinfo.domain.social.SocialAuthProvider;
import com.njydsz.userinfo.domain.social.SocialUserInfo;
import com.njydsz.userinfo.server.config.SocialAuthProperties;

/**
 * 钉钉 OAuth2 认证提供者
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
@RequiredArgsConstructor
public class DingTalkAuthProvider implements SocialAuthProvider {

  /** 钉钉平台标识 */
  private static final String PLATFORM = "DINGTALK";

  /** 钉钉扫码登录授权端点 */
  private static final String AUTHORIZE_URL =
      "https://login.dingtalk.com/oauth2/auth";

  /** 钉钉令牌端点 */
  private static final String ACCESS_TOKEN_URL =
      "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";

  /** 钉钉用户信息端点 */
  private static final String USER_INFO_URL =
      "https://api.dingtalk.com/v1.0/contact/users";

  private final SocialAuthProperties socialAuthProperties;
  private final JustAuthHttpClient httpClient;

  @Override
  public String getPlatform() {
    return PLATFORM;
  }

  @Override
  public String authorize(String state, String redirectUri) {
    SocialAuthProperties.ProviderConfig config =
        socialAuthProperties.getProvider(PLATFORM.toLowerCase());
    if (config == null) {
      throw new SocialAuthException("钉钉配置未找到");
    }

    String clientId = config.getAppId();
    String scope = config.getScope() != null ? config.getScope() : "openid";

    String url = AUTHORIZE_URL
        + "?prompt=consent"
        + "&client_id=" + java.net.URLEncoder.encode(clientId,
            java.nio.charset.StandardCharsets.UTF_8)
        + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri,
            java.nio.charset.StandardCharsets.UTF_8)
        + "&scope=" + java.net.URLEncoder.encode(scope, java.nio.charset.StandardCharsets.UTF_8)
        + "&state=" + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8)
        + "&response_type=code";

    log.debug("钉钉授权 URL 已生成: clientId={}", clientId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config =
        socialAuthProperties.getProvider(PLATFORM.toLowerCase());
    if (config == null) {
      throw new SocialAuthException("钉钉配置未找到");
    }

    Map<String, String> tokenParams = new HashMap<>();
    tokenParams.put("clientId", config.getAppId());
    tokenParams.put("clientSecret", config.getAppSecret());
    tokenParams.put("code", code);
    tokenParams.put("grantType", "authorization_code");

    Map<String, Object> tokenResponse = httpClient.postJsonForMap(ACCESS_TOKEN_URL, tokenParams);

    String accessToken = getStringValue(tokenResponse, "accessToken");
    if (accessToken == null || accessToken.isBlank()) {
      throw new SocialAuthException("钉钉获取 access_token 失败");
    }

    Long expireIn = getLongValue(tokenResponse, "expireIn", 7200L);
    String unionId = getStringValue(tokenResponse, "unionId");

    return new SocialAccessToken(
        accessToken,
        null,
        expireIn,
        unionId,
        unionId);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    // 钉钉通过 access_token 查询用户信息
    String userInfoUrl = USER_INFO_URL + "/" + token.openId();
    Map<String, Object> userResponse = httpClient.getForMap(
        userInfoUrl, token.accessToken(), null);

    String nick = getStringValue(userResponse, "nick");
    String avatar = getStringValue(userResponse, "avatarUrl");
    String email = getStringValue(userResponse, "email");
    String unionId = getStringValue(userResponse, "unionId");

    return new SocialUserInfo(
        token.openId(),
        unionId,
        nick,
        avatar,
        email,
        PLATFORM);
  }

  /**
   * 从响应 Map 中获取字符串值
   *
   * @param map 响应 Map
   * @param key 键
   * @return 值，不存在返回 null
   */
  private String getStringValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }

  /**
   * 从响应 Map 中获取长整数值
   *
   * @param map 响应 Map
   * @param key 键
   * @param defaultValue 默认值
   * @return 值
   */
  private Long getLongValue(Map<String, Object> map, String key, Long defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return defaultValue;
  }
}
