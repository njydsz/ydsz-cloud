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
 * 微信 OAuth2 认证提供者。
 *
 * <p>实现微信网页授权登录流程：
 *
 * <ol>
 *   <li>生成授权 URL（微信 OAuth2 snsapi_login / snsapi_userinfo）</li>
 *   <li>用 code 换取 access_token</li>
 *   <li>获取用户信息（openid、nickname、headimgurl、email）</li>
 * </ol>
 *
 * <p><b>微信 OAuth2 文档：</b>
 * <a href="https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/Wechat_webpage_authorization.html">
 * 微信开放平台文档</a>
 *
 * <p><b>配置要求：</b>需要在微信开放平台注册网站应用，获取 appid + appsecret，
 * 并授权 {@code snsapi_login} 或 {@code snsapi_userinfo} 作用域。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
public class WechatAuthProvider extends AbstractSocialAuthProvider {

  /** 请求参数 Map 初始容量 */
  private static final int PARAMS_CAPACITY = 16;


  /** 微信平台标识 */
  private static final String PLATFORM = "WECHAT";

  /** 微信授权端点（可通过 ydsz.userinfo.social.providers.wechat.authorize-url 覆盖） */
  private static final String DEFAULT_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/qrconnect";

  /** 微信令牌端点（可通过 ydsz.userinfo.social.providers.wechat.access-token-url 覆盖） */
  private static final String DEFAULT_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

  /** 微信用户信息端点（可通过 ydsz.userinfo.social.providers.wechat.user-info-url 覆盖） */
  private static final String DEFAULT_USER_INFO_URL = "https://api.weixin.qq.com/sns/userinfo";

  /** 默认令牌过期时间（秒） */
  private static final long DEFAULT_EXPIRE_IN = 7200L;

  /**
   * 构造微信认证提供者。
   *
   * @param socialAuthProperties 社交认证配置
   * @param httpClient HTTP 客户端
   */
  public WechatAuthProvider(SocialAuthProperties socialAuthProperties,
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
      throw new SocialAuthException(PLATFORM, "微信配置未找到");
    }

    String appId = config.getAppId();
    String scope = config.getScope() != null ? config.getScope() : "snsapi_login";
    String authorizeUrl = config.getOrDefaultAuthorizeUrl(DEFAULT_AUTHORIZE_URL);

    String url = authorizeUrl
        + "?appid=" + urlEncode(appId)
        + "&redirect_uri=" + urlEncode(redirectUri)
        + "&response_type=code"
        + "&scope=" + urlEncode(scope)
        + "&state=" + urlEncode(state)
        + "#wechat_redirect";

    log.debug("微信授权 URL 已生成: appId={}", appId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "微信配置未找到");
    }

    String tokenUrl = config.getOrDefaultAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL);

    Map<String, String> tokenParams = new HashMap<>(PARAMS_CAPACITY);
    tokenParams.put("appid", config.getAppId());
    tokenParams.put("secret", config.getAppSecret());
    tokenParams.put("code", code);
    tokenParams.put("grant_type", "authorization_code");

    Map<String, Object> tokenResponse = httpClient.getForMap(tokenUrl, null, tokenParams);

    String errcode = getStr(tokenResponse, "errcode");
    if (errcode != null && !errcode.isBlank() && !"0".equals(errcode)) {
      String errmsg = getStr(tokenResponse, "errmsg");
      throw new SocialAuthException(PLATFORM, "微信获取 access_token 失败: " + errmsg);
    }

    String accessToken = getStr(tokenResponse, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new SocialAuthException(PLATFORM, "微信获取 access_token 失败: 未返回 access_token");
    }

    Long expireIn = getLong(tokenResponse, "expires_in", DEFAULT_EXPIRE_IN);
    String openId = getStr(tokenResponse, "openid");
    String unionId = getStr(tokenResponse, "unionid");

    if (openId == null || openId.isBlank()) {
      throw new SocialAuthException(PLATFORM, "微信获取 access_token 失败: 未返回 openid");
    }

    return new SocialAccessToken(accessToken, null, expireIn, openId, unionId);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "微信配置未找到");
    }

    String userInfoUrl = config.getOrDefaultUserInfoUrl(DEFAULT_USER_INFO_URL);

    Map<String, String> params = new HashMap<>(PARAMS_CAPACITY);
    params.put("access_token", token.accessToken());
    params.put("openid", token.openId());
    params.put("lang", "zh_CN");

    Map<String, Object> userResponse = httpClient.getForMap(userInfoUrl, null, params);

    String errcode = getStr(userResponse, "errcode");
    if (errcode != null && !errcode.isBlank() && !"0".equals(errcode)) {
      String errmsg = getStr(userResponse, "errmsg");
      throw new SocialAuthException(PLATFORM, "微信获取用户信息失败: " + errmsg);
    }

    String nickname = getStr(userResponse, "nickname");
    String avatar = getStr(userResponse, "headimgurl");
    String unionId = getStr(userResponse, "unionid");

    return new SocialUserInfo(token.openId(), unionId, nickname, avatar, null, PLATFORM);
  }
}
