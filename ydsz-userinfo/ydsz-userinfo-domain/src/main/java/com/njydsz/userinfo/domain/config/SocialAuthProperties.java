package com.njydsz.userinfo.domain.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 社交认证配置属性。
 *
 * <p>集中管理社交登录（OAuth2）的平台配置，支持企业微信、钉钉、飞书等多平台。
 * 通过 {@code @EnableConfigurationProperties} 注册。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.social}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     social:
 *       enabled: true
 *       providers:
 *         enterprise_wechat:
 *           app-id: ${WECHAT_CORP_ID:}
 *           app-secret: ${WECHAT_CORP_SECRET:}
 *           scope: agentid
 *         dingtalk:
 *           app-id: ${DINGTALK_APP_ID:}
 *           app-secret: ${DINGTALK_APP_SECRET:}
 *           scope: openid
 *         feishu:
 *           app-id: ${FEISHU_APP_ID:}
 *           app-secret: ${FEISHU_APP_SECRET:}
 *           scope: openid
 * </pre>
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.social")
@SuppressWarnings("checkstyle:MagicNumber")
public class SocialAuthProperties {

  /** 社交认证全局开关（默认 false，需显式开启）。 */
  private boolean enabled = false;

  /** 各平台配置（key 为平台标识，如 enterprise_wechat/dingtalk/feishu）。 */
  private Map<String, ProviderConfig> providers = new HashMap<>();

  /**
   * 平台提供者配置。
   *
   * <p>每个平台独立的 OAuth2 端点与凭据配置。
   *
   * @author ydsz-team
   * @since 1.6.0
   */
  @Data
  public static class ProviderConfig {

    /** 应用 ID（平台分配 appId / clientId） */
    private String appId;

    /** 应用密钥（平台分配 appSecret / clientSecret，建议通过环境变量注入） */
    private String appSecret;

    /** OAuth2 授权范围（scope，格式依平台而定） */
    private String scope;

    /**
     * 平台回调地址（redirectUri）。
     *
     * <p>授权完成后平台重定向回应用的完整 URL，如 {@code https://example.com/api/v1/auth/social/callback/dingtalk}。
     * 必须与平台管理后台注册的回调地址完全一致，否则授权平台会拒绝请求。
     */
    private String redirectUri;

    /**
     * 自定义授权端点 URL（可选）。
     *
     * <p>用于私有化部署或内部代理场景，覆盖默认的公开端点。为空时使用平台默认端点。
     */
    private String authorizeUrl;

    /**
     * 自定义令牌端点 URL（可选）。
     *
     * <p>用于私有化部署或内部代理场景，覆盖默认的公开端点。为空时使用平台默认端点。
     */
    private String accessTokenUrl;

    /**
     * 自定义用户信息端点 URL（可选）。
     *
     * <p>用于私有化部署或内部代理场景，覆盖默认的公开端点。为空时使用平台默认端点。
     */
    private String userInfoUrl;

    /**
     * 自定义用户详情端点 URL（可选）。
     *
     * <p>用于私有化部署或内部代理场景，覆盖默认的公开端点。为空时使用平台默认端点。
     * <p>仅有部分平台需要独立的详情端点（如企业微信），不需要时保持 null 即可。
     */
    private String userDetailUrl;

    /**
     * 获取授权端点 URL（优先使用自定义值，为空时返回默认值）。
     *
     * @param defaultUrl 默认端点 URL
     * @return 实际使用的端点 URL
     */
    public String getOrDefaultAuthorizeUrl(String defaultUrl) {
      return (authorizeUrl != null && !authorizeUrl.isBlank()) ? authorizeUrl : defaultUrl;
    }

    /**
     * 获取令牌端点 URL（优先使用自定义值，为空时返回默认值）。
     *
     * @param defaultUrl 默认端点 URL
     * @return 实际使用的端点 URL
     */
    public String getOrDefaultAccessTokenUrl(String defaultUrl) {
      return (accessTokenUrl != null && !accessTokenUrl.isBlank()) ? accessTokenUrl : defaultUrl;
    }

    /**
     * 获取用户信息端点 URL（优先使用自定义值，为空时返回默认值）。
     *
     * @param defaultUrl 默认端点 URL
     * @return 实际使用的端点 URL
     */
    public String getOrDefaultUserInfoUrl(String defaultUrl) {
      return (userInfoUrl != null && !userInfoUrl.isBlank()) ? userInfoUrl : defaultUrl;
    }

    /**
     * 获取用户详情端点 URL（优先使用自定义值，为空时返回默认值）。
     *
     * @param defaultUrl 默认端点 URL
     * @return 实际使用的端点 URL
     */
    public String getOrDefaultUserDetailUrl(String defaultUrl) {
      return (userDetailUrl != null && !userDetailUrl.isBlank()) ? userDetailUrl : defaultUrl;
    }
  }

  /**
   * 根据平台标识获取提供者配置。
   *
   * @param platform 平台标识（大小写不敏感）
   * @return 平台配置，未找到返回 null
   */
  public ProviderConfig getProvider(String platform) {
    if (platform == null || platform.isBlank()) {
      return null;
    }
    return providers.get(platform.toLowerCase());
  }

  /**
   * 判断指定平台是否已配置。
   *
   * @param platform 平台标识
   * @return true 表示已配置
   */
  public boolean hasProvider(String platform) {
    return platform != null && providers.containsKey(platform.toLowerCase());
  }
}
