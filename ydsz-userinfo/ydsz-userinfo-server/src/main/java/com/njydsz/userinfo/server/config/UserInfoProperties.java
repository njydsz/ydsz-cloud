package com.njydsz.userinfo.server.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户信息中心配置属性
 *
 * <p>集中管理用户中心（ydsz-userinfo）的安全参数与会话策略， 替代分散的 {@code @Value} 硬编码常量。 通过 {@link
 * UserInfoConfiguration} 的 {@code @EnableConfigurationProperties} 注册。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo}
 *
 * <p><b>配置分组：</b>
 *
 * <ul>
 *   <li><b>Token 会话</b>：{@link #tokenTtlSeconds}（access_token 有效期）
 *   <li><b>登录安全</b>：{@link #maxLoginFailCount}、{@link #lockDurationMinutes}、{@link
 *       #captchaEnabled}、{@link #captchaTtlSeconds}
 *   <li><b>密码策略</b>：{@link #passwordMinLength}、{@link #passwordMaxLength}、{@link
 *       #passwordMinCategoryCount}、{@link #bcryptStrength}
 *   <li><b>健康检查</b>：{@link #healthEnabled}
 *   <li><b>OAuth2</b>：{@link #oauth2Clients}（clientId → 客户端配置）
 * </ul>
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     token-ttl-seconds: 7200
 *     max-login-fail-count: 5
 *     lock-duration-minutes: 30
 *     captcha-enabled: true
 *     captcha-ttl-seconds: 300
 *     password-min-length: 8
 *     password-max-length: 64
 *     password-min-category-count: 3
 *     bcrypt-strength: 10
 *     oauth2-clients:
 *       third-party-app:
 *         client-secret: ${OAUTH2_CLIENT_SECRET:default-secret}
 *         redirect-uris:
 *           - https://example.com/callback
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo")
@SuppressWarnings("checkstyle:MagicNumber")
public class UserInfoProperties {

  /** 默认 access_token 有效期：2 小时（7200 秒）。 */
  private static final long DEFAULT_TOKEN_TTL_SECONDS = 7200;

  /** 默认最大登录失败次数。 */
  private static final int DEFAULT_MAX_LOGIN_FAIL_COUNT = 5;

  /** 默认账号锁定时长：30 分钟。 */
  private static final int DEFAULT_LOCK_DURATION_MINUTES = 30;

  /** 默认图形验证码有效期：5 分钟（300 秒）。 */
  private static final long DEFAULT_CAPTCHA_TTL_SECONDS = 300;

  /** 默认密码最小长度。 */
  private static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;

  /** 默认密码最大长度（BCrypt 72 字节截断限制）。 */
  private static final int DEFAULT_PASSWORD_MAX_LENGTH = 64;

  /** 默认密码最少字符种类数。 */
  private static final int DEFAULT_PASSWORD_MIN_CATEGORY_COUNT = 3;

  /** 默认 BCrypt 加密强度。 */
  private static final int DEFAULT_BCRYPT_STRENGTH = 10;

  /** 默认密码历史保留条数。 */
  private static final int DEFAULT_PASSWORD_HISTORY_COUNT = 5;

  /** 默认批量查询上限。 */
  private static final int DEFAULT_BATCH_SIZE_LIMIT = 500;

  /** access_token 有效期（秒），默认 2 小时。 */
  private long tokenTtlSeconds = DEFAULT_TOKEN_TTL_SECONDS;

  /** 最大登录失败次数。 */
  private int maxLoginFailCount = DEFAULT_MAX_LOGIN_FAIL_COUNT;

  /** 账号锁定时长（分钟），默认 30 分钟。 */
  private int lockDurationMinutes = DEFAULT_LOCK_DURATION_MINUTES;

  /** 登录时是否强制要求图形验证码。 */
  private boolean captchaEnabled = true;

  /** 图形验证码有效期（秒），默认 5 分钟。 */
  private long captchaTtlSeconds = DEFAULT_CAPTCHA_TTL_SECONDS;

  /** 健康检查是否启用。 */
  private boolean healthEnabled = true;

  /** 密码最小长度。 */
  private int passwordMinLength = DEFAULT_PASSWORD_MIN_LENGTH;

  /** 密码最大长度。 */
  private int passwordMaxLength = DEFAULT_PASSWORD_MAX_LENGTH;

  /** 密码最少字符种类数（大写/小写/数字/特殊字符）。 */
  private int passwordMinCategoryCount = DEFAULT_PASSWORD_MIN_CATEGORY_COUNT;

  /** BCrypt 加密强度（4-31）。 */
  private int bcryptStrength = DEFAULT_BCRYPT_STRENGTH;

  /** OAuth2 客户端注册表（clientId → 客户端配置）。 */
  private Map<String, OAuth2Client> oauth2Clients = new HashMap<>();

  /** 密码历史记录保留条数。 */
  private int passwordHistoryCount = DEFAULT_PASSWORD_HISTORY_COUNT;

  /**
   * 批量查询上限（单次 IN 查询最大 ID 数）。
   *
   * <p>防止调用方传入过多 ID 导致巨型 IN 查询，超出时自动分批执行。
   */
  private int batchSizeLimit = DEFAULT_BATCH_SIZE_LIMIT;

  /**
   * OAuth2 客户端配置
   *
   * <p>由 {@link com.njydsz.userinfo.web.controller.OAuth2Controller} 在 {@code /authorize} 和 {@code
   * /token} 端点校验 clientId / clientSecret / redirectUri。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  @Data
  public static class OAuth2Client {
    /** 客户端密钥：与 clientId 配对，在 /token 端点强制校验，建议存储在密钥管理服务 */
    private String clientSecret;

    /** 允许的回调地址白名单（RFC 6749 §3.1.2.3）：防止开放重定向攻击 */
    private List<String> redirectUris;
  }

  /**
   * OAuth2 客户端密钥校验
   *
   * <p>同时校验 clientId 是否注册 + clientSecret 是否匹配。 任意参数为 null 时直接返回 false（防御性编程）。
   *
   * @param clientId 客户端 ID
   * @param clientSecret 客户端密钥
   * @return true 校验通过；false 客户端未注册或密钥不匹配
   */
  public boolean validateOAuth2Client(String clientId, String clientSecret) {
    if (clientId == null || clientSecret == null) {
      return false;
    }
    OAuth2Client client = oauth2Clients.get(clientId);
    return client != null && clientSecret.equals(client.getClientSecret());
  }
}
