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
 * 企业微信 OAuth2 认证提供者
 *
 * <p>实现企业微信扫码登录流程：
 *
 * <ol>
 *   <li>生成授权 URL（企业微信网页授权登录）</li>
 *   <li>用 code 换取 access_token</li>
 *   <li>获取用户信息（userId、name、avatar、email）</li>
 * </ol>
 *
 * <p><b>企业微信 OAuth2 文档：</b>
 * <a href="https://developer.work.weixin.qq.com/document/path/91022">企业微信接入文档</a>
 *
 * <p><b>配置要求：</b>需要在企业微信管理后台创建应用，获取 corpid + agentid + secret。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnterpriseWechatAuthProvider implements SocialAuthProvider {

  /** 企业微信平台标识 */
  private static final String PLATFORM = "ENTERPRISE_WECHAT";

  /** 企业微信授权端点 */
  private static final String AUTHORIZE_URL =
      "https://open.work.weixin.qq.com/wwopen/sso/3rd_qrConnect";

  /** 企业微信令牌端点 */
  private static final String ACCESS_TOKEN_URL =
      "https://qyapi.weixin.qq.com/cgi-bin/gettoken";

  /** 企业微信用户信息端点 */
  private static final String USER_INFO_URL =
      "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo";

  /** 企业微信用户详情端点 */
  private static final String USER_DETAIL_URL =
      "https://qyapi.weixin.qq.com/cgi-bin/user/get";

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
      throw new SocialAuthException("企业微信配置未找到");
    }

    String appId = config.getAppId();
    String agentId = config.getScope(); // 企业微信使用 scope 字段存储 agentid

    String url = AUTHORIZE_URL
        + "?appid=" + java.net.URLEncoder.encode(appId, java.nio.charset.StandardCharsets.UTF_8)
        + "&agentid=" + java.net.URLEncoder.encode(agentId, java.nio.charset.StandardCharsets.UTF_8)
        + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri,
            java.nio.charset.StandardCharsets.UTF_8)
        + "&state=" + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8);

    log.debug("企业微信授权 URL 已生成: appId={}", appId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config =
        socialAuthProperties.getProvider(PLATFORM.toLowerCase());
    if (config == null) {
      throw new SocialAuthException("企业微信配置未找到");
    }

    // 企业微信需要先获取 access_token（企业级别的）
    Map<String, String> tokenParams = new HashMap<>();
    tokenParams.put("corpid", config.getAppId());
    tokenParams.put("corpsecret", config.getAppSecret());

    Map<String, Object> tokenResponse = httpClient.postFormForMap(ACCESS_TOKEN_URL, tokenParams);

    String accessToken = getStringValue(tokenResponse, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      int errcode = getIntValue(tokenResponse, "errcode", -1);
      String errmsg = getStringValue(tokenResponse, "errmsg");
      throw new SocialAuthException("企业微信获取 access_token 失败: " + errcode + " - " + errmsg);
    }

    // 用 access_token 和 code 获取用户信息
    Map<String, String> userParams = new HashMap<>();
    userParams.put("access_token", accessToken);
    userParams.put("code", code);

    Map<String, Object> userResponse = httpClient.getForMap(USER_INFO_URL, null, userParams);

    String userId = getStringValue(userResponse, "UserId");
    if (userId == null || userId.isBlank()) {
      throw new SocialAuthException("企业微信获取用户信息失败: 未返回 UserId");
    }

    return new SocialAccessToken(
        accessToken,
        null,
        7200,
        userId,
        null);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    SocialAuthProperties.ProviderConfig config =
        socialAuthProperties.getProvider(PLATFORM.toLowerCase());
    if (config == null) {
      throw new SocialAuthException("企业微信配置未找到");
    }

    // 获取用户详情
    Map<String, String> params = new HashMap<>();
    params.put("access_token", token.accessToken());
    params.put("userid", token.openId());

    Map<String, Object> detailResponse = httpClient.getForMap(USER_DETAIL_URL, null, params);

    String name = getStringValue(detailResponse, "name");
    String avatar = getStringValue(detailResponse, "avatar");
    String email = getStringValue(detailResponse, "email");
    String mobile = getStringValue(detailResponse, "mobile");

    return new SocialUserInfo(
        token.openId(),
        null,
        name,
        avatar,
        email != null ? email : mobile,
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
   * 从响应 Map 中获取整数值
   *
   * @param map 响应 Map
   * @param key 键
   * @param defaultValue 默认值
   * @return 值
   */
  private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return defaultValue;
  }
}
