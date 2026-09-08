package com.njydsz.userinfo.infra.social;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.config.SocialAuthProperties;
import com.njydsz.userinfo.domain.social.SocialAccessToken;
import com.njydsz.userinfo.domain.social.SocialAuthException;
import com.njydsz.userinfo.domain.social.SocialUserInfo;

/**
 * QQ OAuth2 认证提供者。
 *
 * <p>实现 QQ 互联网页授权登录流程：
 *
 * <ol>
 *   <li>生成授权 URL（QQ OAuth2 授权码模式）</li>
 *   <li>用 code 换取 access_token（QQ 返回 JSONP 格式需解析）</li>
 *   <li>获取 openid + 用户信息（nickname、figureurl）</li>
 * </ol>
 *
 * <p><b>QQ OAuth2 文档：</b>
 * <a href="https://wiki.connect.qq.com/%E4%BD%BF%E7%94%A8authorization_code%E8%8E%B7%E5%8F%96access_token">
 * QQ 互联接入文档</a>
 *
 * <p><b>特殊处理：</b>QQ 的 access_token 接口返回 JSONP 格式（callback( {...} );），
 * 需要剥离回调函数包裹后再解析 JSON。
 *
 * <p><b>配置要求：</b>需要在 QQ 互联开放平台注册网站应用，获取 appid + appkey。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
public class QqAuthProvider extends AbstractSocialAuthProvider {

  /** 请求参数 Map 初始容量 */
  private static final int PARAMS_CAPACITY = 16;

  /** QQ 平台标识 */
  private static final String PLATFORM = "QQ";

  /** QQ 授权端点（可通过 ydsz.userinfo.social.providers.qq.authorize-url 覆盖） */
  private static final String DEFAULT_AUTHORIZE_URL = "https://graph.qq.com/oauth2.0/authorize";

  /** QQ 令牌端点（可通过 ydsz.userinfo.social.providers.qq.access-token-url 覆盖） */
  private static final String DEFAULT_ACCESS_TOKEN_URL = "https://graph.qq.com/oauth2.0/token";

  /** QQ openid 端点（可通过 ydsz.userinfo.social.providers.qq.user-info-url 覆盖） */
  private static final String DEFAULT_OPENID_URL = "https://graph.qq.com/oauth2.0/me";

  /** QQ 用户信息端点 */
  private static final String DEFAULT_USER_INFO_URL = "https://graph.qq.com/user/get_user_info";

  /** 默认令牌过期时间（秒） */
  private static final long DEFAULT_EXPIRE_IN = 7776000L;

  /** JSONP 回调包裹正则 */
  private static final Pattern JSONP_PATTERN = Pattern.compile("callback\\((.+)\\);\\s*");

  /** QQ access_token 响应中的 key 名 */
  private static final String EXPIRES_IN_KEY = "expires_in";

  /**
   * 构造 QQ 认证提供者。
   *
   * @param socialAuthProperties 社交认证配置
   * @param httpClient HTTP 客户端
   */
  public QqAuthProvider(SocialAuthProperties socialAuthProperties,
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
      throw new SocialAuthException(PLATFORM, "QQ配置未找到");
    }

    String appId = config.getAppId();
    String scope = config.getScope() != null ? config.getScope() : "get_user_info";
    String authorizeUrl = config.getOrDefaultAuthorizeUrl(DEFAULT_AUTHORIZE_URL);

    String url = authorizeUrl
        + "?response_type=code"
        + "&client_id=" + urlEncode(appId)
        + "&redirect_uri=" + urlEncode(redirectUri)
        + "&scope=" + urlEncode(scope)
        + "&state=" + urlEncode(state);

    log.debug("QQ 授权 URL 已生成: appId={}", appId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "QQ配置未找到");
    }

    String tokenUrl = config.getOrDefaultAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL);

    Map<String, String> tokenParams = new HashMap<>(PARAMS_CAPACITY);
    tokenParams.put("grant_type", "authorization_code");
    tokenParams.put("client_id", config.getAppId());
    tokenParams.put("client_secret", config.getAppSecret());
    tokenParams.put("code", code);
    tokenParams.put("redirect_uri", redirectUri);
    tokenParams.put("fmt", "json");

    // QQ token 接口返回 JSONP，指定 fmt=json 可返回纯 JSON
    Map<String, Object> tokenResponse = httpClient.getForMap(tokenUrl, null, tokenParams);

    String accessToken = getStr(tokenResponse, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new SocialAuthException(PLATFORM, "QQ 获取 access_token 失败");
    }

    Long expireIn = getLong(tokenResponse, EXPIRES_IN_KEY, DEFAULT_EXPIRE_IN);

    // 获取 openid
    String openId = fetchOpenId(accessToken);
    if (openId == null || openId.isBlank()) {
      throw new SocialAuthException(PLATFORM, "QQ 获取 openid 失败");
    }

    return new SocialAccessToken(accessToken, null, expireIn, openId, null);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException(PLATFORM, "QQ配置未找到");
    }

    String userInfoUrl = config.getOrDefaultUserInfoUrl(DEFAULT_USER_INFO_URL);

    Map<String, String> params = new HashMap<>(PARAMS_CAPACITY);
    params.put("access_token", token.accessToken());
    params.put("oauth_consumer_key", config.getAppId());
    params.put("openid", token.openId());

    Map<String, Object> userResponse = httpClient.getForMap(userInfoUrl, null, params);

    String ret = getStr(userResponse, "ret");
    if (ret != null && !"0".equals(ret)) {
      String msg = getStr(userResponse, "msg");
      throw new SocialAuthException(PLATFORM, "QQ 获取用户信息失败: " + msg);
    }

    String nickname = getStr(userResponse, "nickname");
    String avatar = getStr(userResponse, "figureurl_qq_2");
    if (avatar == null) {
      avatar = getStr(userResponse, "figureurl_qq_1");
    }

    return new SocialUserInfo(token.openId(), null, nickname, avatar, null, PLATFORM);
  }

  /**
   * 获取 QQ openid。
   *
   * <p>QQ openid 接口返回 JSONP 格式，指定 fmt=json 后返回纯 JSON。
   *
   * @param accessToken 访问令牌
   * @return openid，获取失败返回 null
   */
  private String fetchOpenId(String accessToken) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    String openIdUrl = config.getOrDefaultOpenidUrl(DEFAULT_OPENID_URL);

    Map<String, String> params = new HashMap<>(PARAMS_CAPACITY);
    params.put("access_token", accessToken);
    params.put("fmt", "json");

    Map<String, Object> response = httpClient.getForMap(openIdUrl, null, params);
    return getStr(response, "openid");
  }
}
