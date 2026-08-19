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
import com.njydsz.userinfo.domain.config.SocialAuthProperties;

/**
 * 飞书 OAuth2 认证提供者
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
@RequiredArgsConstructor
public class FeishuAuthProvider implements SocialAuthProvider {

  /** 飞书平台标识 */
  private static final String PLATFORM = "FEISHU";

  /** 飞书授权端点 */
  private static final String AUTHORIZE_URL =
      "https://open.feishu.cn/open-apis/authen/v1/authorize";

  /** 飞书令牌端点 */
  private static final String ACCESS_TOKEN_URL =
      "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";

  /** 飞书用户信息端点 */
  private static final String USER_INFO_URL =
      "https://open.feishu.cn/open-apis/authen/v1/user_info";

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
      throw new SocialAuthException("飞书配置未找到");
    }

    String appId = config.getAppId();
    String scope = config.getScope() != null ? config.getScope() : "openid";

    String url = AUTHORIZE_URL
        + "?app_id=" + java.net.URLEncoder.encode(appId, java.nio.charset.StandardCharsets.UTF_8)
        + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri,
            java.nio.charset.StandardCharsets.UTF_8)
        + "&scope=" + java.net.URLEncoder.encode(scope, java.nio.charset.StandardCharsets.UTF_8)
        + "&state=" + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8);

    log.debug("飞书授权 URL 已生成: appId={}", appId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config =
        socialAuthProperties.getProvider(PLATFORM.toLowerCase());
    if (config == null) {
      throw new SocialAuthException("飞书配置未找到");
    }

    Map<String, String> tokenParams = new HashMap<>();
    tokenParams.put("app_id", config.getAppId());
    tokenParams.put("app_secret", config.getAppSecret());
    tokenParams.put("code", code);
    tokenParams.put("grant_type", "authorization_code");
    tokenParams.put("redirect_uri", redirectUri);

    Map<String, Object> tokenResponse = httpClient.postJsonForMap(ACCESS_TOKEN_URL, tokenParams);

    // 飞书返回嵌套结构：data.access_token
    Object data = tokenResponse.get("data");
    if (!(data instanceof Map)) {
      Integer codeObj = getIntegerValue(tokenResponse, "code");
      String msg = getStringValue(tokenResponse, "msg");
      throw new SocialAuthException("飞书获取 access_token 失败: " + codeObj + " - " + msg);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> dataMap = (Map<String, Object>) data;
    String accessToken = getStringValue(dataMap, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new SocialAuthException("飞书获取 access_token 失败：响应中未包含 access_token");
    }

    Long expire = getLongValue(dataMap, "expire_in", 7200L);
    String openId = getStringValue(dataMap, "open_id");

    return new SocialAccessToken(
        accessToken,
        getStringValue(dataMap, "refresh_token"),
        expire,
        openId,
        openId);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    Map<String, Object> userResponse = httpClient.getForMap(
        USER_INFO_URL, token.accessToken(), null);

    Object data = userResponse.get("data");
    if (!(data instanceof Map)) {
      throw new SocialAuthException("飞书获取用户信息失败");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> dataMap = (Map<String, Object>) data;

    String name = getStringValue(dataMap, "name");
    String avatar = getStringValue(dataMap, "avatar_url");
    String email = getStringValue(dataMap, "email");
    String openId = getStringValue(dataMap, "open_id");

    return new SocialUserInfo(
        openId,
        openId,
        name,
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

  /**
   * 从响应 Map 中获取整数值
   *
   * @param map 响应 Map
   * @param key 键
   * @param defaultValue 默认值
   * @return 值
   */
  private Integer getIntegerValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return null;
  }
}
