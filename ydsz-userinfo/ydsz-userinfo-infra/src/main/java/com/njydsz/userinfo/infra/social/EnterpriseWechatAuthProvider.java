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
 * 企业微信 OAuth2 认证提供者。
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
 * @since 26.09.01
 */
@Slf4j
@Component
public class EnterpriseWechatAuthProvider extends AbstractSocialAuthProvider {

  /** 企业微信平台标识 */
  private static final String PLATFORM = "ENTERPRISE_WECHAT";

  /** 企业微信授权端点（可通过 ydsz.userinfo.social.providers.enterprise_wechat.authorize-url 覆盖） */
  private static final String DEFAULT_AUTHORIZE_URL = "https://open.work.weixin.qq.com/wwopen/sso/3rd_qrConnect";

  /** 企业微信令牌端点（可通过 ydsz.userinfo.social.providers.enterprise_wechat.access-token-url 覆盖） */
  private static final String DEFAULT_ACCESS_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";

  /** 企业微信用户信息端点（可通过 ydsz.userinfo.social.providers.enterprise_wechat.user-info-url 覆盖） */
  private static final String DEFAULT_USER_INFO_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo";

  /** 企业微信用户详情端点（可通过 ydsz.userinfo.social.providers.enterprise_wechat.user-detail-url 覆盖） */
  private static final String DEFAULT_USER_DETAIL_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/get";

  /** 默认令牌过期时间（秒） */
  private static final long DEFAULT_EXPIRE_IN = 7200L;

  /**
   * 构造企业微信认证提供者。
   *
   * @param socialAuthProperties 社交认证配置
   * @param httpClient HTTP 客户端
   */
  public EnterpriseWechatAuthProvider(SocialAuthProperties socialAuthProperties,
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
      throw new SocialAuthException("企业微信配置未找到");
    }

    String appId = config.getAppId();
    String agentId = config.getScope(); // 企业微信使用 scope 字段存储 agentid

    String authorizeUrl = config.getOrDefaultAuthorizeUrl(DEFAULT_AUTHORIZE_URL);

    String url = authorizeUrl
        + "?appid=" + urlEncode(appId)
        + "&agentid=" + urlEncode(agentId)
        + "&redirect_uri=" + urlEncode(redirectUri)
        + "&state=" + urlEncode(state);

    log.debug("企业微信授权 URL 已生成: appId={}", appId);
    return url;
  }

  @Override
  public SocialAccessToken exchangeToken(String code, String redirectUri) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException("企业微信配置未找到");
    }

    // 企业微信需要先获取 access_token（企业级别的）
    String tokenUrl = config.getOrDefaultAccessTokenUrl(DEFAULT_ACCESS_TOKEN_URL);

    Map<String, String> tokenParams = new HashMap<>();
    tokenParams.put("corpid", config.getAppId());
    tokenParams.put("corpsecret", config.getAppSecret());

    Map<String, Object> tokenResponse = httpClient.postFormForMap(tokenUrl, tokenParams);

    String accessToken = getStr(tokenResponse, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      int errcode = getInt(tokenResponse, "errcode", -1);
      String errmsg = getStr(tokenResponse, "errmsg");
      throw new SocialAuthException("企业微信获取 access_token 失败: " + errcode + " - " + errmsg);
    }

    // 用 access_token 和 code 获取用户信息
    String userInfoUrl = config.getOrDefaultUserInfoUrl(DEFAULT_USER_INFO_URL);
    Map<String, String> userParams = new HashMap<>();
    userParams.put("access_token", accessToken);
    userParams.put("code", code);

    Map<String, Object> userResponse = httpClient.getForMap(userInfoUrl, null, userParams);

    String userId = getStr(userResponse, "UserId");
    if (userId == null || userId.isBlank()) {
      throw new SocialAuthException("企业微信获取用户信息失败: 未返回 UserId");
    }

    return new SocialAccessToken(accessToken, null, DEFAULT_EXPIRE_IN, userId, null);
  }

  @Override
  public SocialUserInfo getUserInfo(SocialAccessToken token) {
    SocialAuthProperties.ProviderConfig config = getProviderConfig();
    if (config == null) {
      throw new SocialAuthException("企业微信配置未找到");
    }

    // 获取用户详情
    String detailUrl = config.getOrDefaultUserDetailUrl(DEFAULT_USER_DETAIL_URL);
    Map<String, String> params = new HashMap<>();
    params.put("access_token", token.accessToken());
    params.put("userid", token.openId());

    Map<String, Object> detailResponse = httpClient.getForMap(detailUrl, null, params);

    String name = getStr(detailResponse, "name");
    String avatar = getStr(detailResponse, "avatar");
    String email = getStr(detailResponse, "email");
    String mobile = getStr(detailResponse, "mobile");

    return new SocialUserInfo(
        token.openId(), null, name, avatar, email != null ? email : mobile, PLATFORM);
  }
}
