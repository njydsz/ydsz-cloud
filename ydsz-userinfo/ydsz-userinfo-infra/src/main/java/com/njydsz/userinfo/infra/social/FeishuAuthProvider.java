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
 * 飞书 OAuth2 认证提供者。
 *
 * <p>实现飞书应用授权登录流程：
 *
 * <ol>
 *   <li>生成授权 URL（飞书 OAuth2 授权码模式）</li>
 *   <li>用 code 换取 tenant_access_token 和 user_access_token</li>
 *   <li>获取用户信息（open_id、name、avatar、email）</li>
 * </ol>
 *
 * <p><b>飞书 OAuth2 文档：</b>
 * <a href="https://open.feishu.cn/document/server-docs/authentication-management/login-state-management/obtain-user-access-token">
 * 飞书接入文档</a>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class FeishuAuthProvider extends AbstractSocialAuthProvider {

  /** 飞书平台标识 */
  private static final String PLATFORM = "FEISHU";

  /** 飞书授权端点（可通过 ydsz.userinfo.social.providers.feishu.authorize-url 覆盖） */
  private static final String DEFAULT_AUTHORIZE_URL = "https://open.feishu.cn/open-apis/authen/v1/authorize";

  /** 飞书令牌端点（可通过 ydsz.userinfo.social.providers.feishu.access-token-url 覆盖） */
  private static final String DEFAULT_ACCESS_TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";

  /** 飞书用户信息端点（可通过 ydsz.userinfo.social.providers.feishu.user-info-url 覆盖） */
  private static final String DEFAULT_USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";

  /** 默认令牌过期时间（秒） */
  private static final long DEFAULT_EXPIRE_IN = 7200L;

  /**
   * 构造飞书认证提供者。
   *
   * @param socialAuthProperties 社交认证配置
   * @param httpClient HTTP 客户端
   */
  public FeishuAuthProvider(SocialAuthProperties socialAuthProperties,
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
      throw new SocialAuthException("飞书配置未找到");
    }

    String appId = config.getAppId();
    String scope = config.getScope() != null ? config.getScope() : "openid";

    String authorizeUrl = config.getOrDefaultAuthorizeUrl(DEFAULT_AUTHORIZE_URL);

    String url = authorizeUrl
        + "?app_id=" + urlEncode(appId)
        + "&redirect_uri=" + urlEncode(redirectUri)
        + "&scope=" + urlEncode(scope)
        + "&state=" + urlEncode(state);

    log.debug("飞书授权 URL 已生成: appId={}", appId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException("飞书配置未找到");
    }

    String tokenUrl = config.getOrDefaultAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL);

    Map<String, String> tokenParams = new HashMap<>();
    tokenParams.put("app_id", config.getAppId());
    tokenParams.put("app_secret", config.getAppSecret());
    tokenParams.put("code", code);
    tokenParams.put("grant_type", "authorization_code");
    tokenParams.put("redirect_uri", redirectUri);

    Map<String, Object> tokenResponse = httpClient.postJsonForMap(tokenUrl, tokenParams);

    // 飞书返回嵌套结构：data.access_token
    Object data = tokenResponse.get("data");
    if (!(data instanceof Map<?, ?> rawMap)) {
      Integer codeObj = getInt(tokenResponse, "code", null);
      String msg = getStr(tokenResponse, "msg");
      throw new SocialAuthException("飞书获取 access_token 失败: " + codeObj + " - " + msg);
    }

    Map<String, Object> dataMap = new HashMap<>(rawMap.size());
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      dataMap.put(entry.getKey() != null ? entry.getKey().toString() : null, entry.getValue());
    }
    String accessToken = getStr(dataMap, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new SocialAuthException("飞书获取 access_token 失败：响应中未包含 access_token");
    }

    Long expire = getLong(dataMap, "expire_in", DEFAULT_EXPIRE_IN);
    String openId = getStr(dataMap, "open_id");

    return new SocialAccessToken(accessToken, getStr(dataMap, "refresh_token"), expire, openId, openId);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException("飞书配置未找到");
    }

    String userInfoUrl = config.getOrDefaultUserInfoUrl(DEFAULT_USER_INFO_URL);
    Map<String, Object> userResponse = httpClient.getForMap(
        userInfoUrl, token.accessToken(), null);

    Object data = userResponse.get("data");
    if (!(data instanceof Map<?, ?> rawMap)) {
      throw new SocialAuthException("飞书获取用户信息失败");
    }

    Map<String, Object> dataMap = new HashMap<>(rawMap.size());
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      dataMap.put(entry.getKey() != null ? entry.getKey().toString() : null, entry.getValue());
    }

    String name = getStr(dataMap, "name");
    String avatar = getStr(dataMap, "avatar_url");
    String email = getStr(dataMap, "email");
    String openId = getStr(dataMap, "open_id");

    return new SocialUserInfo(openId, openId, name, avatar, email, PLATFORM);
  }
}
