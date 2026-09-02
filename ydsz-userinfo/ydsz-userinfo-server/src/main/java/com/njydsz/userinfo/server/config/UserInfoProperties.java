new HashMap<>(16)mport java.util.List;
import java.util.Map;
import java.util.Set;

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
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo")
public class UserInfoProperties {

  /** 默认riskIpWeight值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_IP_WEIGHT = 30;

  /** 默认riskTimeWeight值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_TIME_WEIGHT = 20;

  /** 默认riskDeviceWeight值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_DEVICE_WEIGHT = 25;

  /** 默认riskFrequencyWeight值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_FREQUENCY_WEIGHT = 25;

  /** 默认riskAnomalyStartHour值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_ANOMALY_START_HOUR = 0;

  /** 默认riskAnomalyEndHour值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_ANOMALY_END_HOUR = 6;

  /** 默认riskFrequencyWindowMinutes值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_FREQUENCY_WINDOW_MINUTES = 5;

  /** 默认riskFrequencyThreshold值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_FREQUENCY_THRESHOLD = 3;

  /** 默认tokenAutoRenewalThresholdPercent值（可被配置文件覆盖） */
  private static final int DEFAULT_TOKEN_AUTO_RENEWAL_THRESHOLD_PERCENT = 10;

  /** 默认alertDedupTtlSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_ALERT_DEDUP_TTL_SECONDS = 300;

  /** 默认alertIpDedupTtlSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_ALERT_IP_DEDUP_TTL_SECONDS = 180;

  /** 默认alertBruteForceThreshold值（可被配置文件覆盖） */
  private static final int DEFAULT_ALERT_BRUTE_FORCE_THRESHOLD = 10;

  /** 默认alertPasswordSprayThreshold值（可被配置文件覆盖） */
  private static final int DEFAULT_ALERT_PASSWORD_SPRAY_THRESHOLD = 5;

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

  /** 默认角色权限 DB 查询结果缓存 TTL（秒）：10 分钟。 */
  private static final long DEFAULT_PERMISSION_CACHE_TTL_SECONDS = 600;

  /** 默认登录风险因子采集窗口（秒）：5 分钟。 */
  private static final long DEFAULT_RISK_WINDOW_SECONDS = 300;

  /** 默认风险等级触发 MFA 的评分阈值（含）。 */
  private static final int DEFAULT_MFA_RISK_THRESHOLD = 60;

  /** 默认单用户最大并发会话数（P1-9，0 表示不限制）。 */
  private static final int DEFAULT_MAX_SESSIONS_PER_USER = 5;

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

  /** 角色权限 DB 查询结果缓存 TTL（秒），默认 10 分钟。 */
  private long permissionCacheTtlSeconds = DEFAULT_PERMISSION_CACHE_TTL_SECONDS;

  /** 登录风险因子采集窗口（秒），默认 5 分钟。 */
  private long riskWindowSeconds = DEFAULT_RISK_WINDOW_SECONDS;

  /** 登录风险等级触发 MFA 的评分阈值（含），默认 60（MEDIUM+）。 */
  private int mfaRiskThreshold = DEFAULT_MFA_RISK_THRESHOLD;

  /**
   * 单用户最大并发会话数（P1-9）。
   *
   * <p>超过上限时自动踢出最早活跃会话，"最多 N 端在线"策略。0 表示不限制。
   */
  private int maxSessionsPerUser = DEFAULT_MAX_SESSIONS_PER_USER;

  // ==================== P0-3 MFA 密钥加密配置 ====================

  /**
   * MFA TOTP 密钥加密密钥（AES-256-GCM）。
   *
   * <p>Base64 编码的 32 字节（256 位）密钥。配置后 MFA 密钥在存入 Redis 前自动加密。
   * 未配置时使用明文存储（仅适用于开发/测试环境）。
   *
   * <p><b>生成方式：</b>{@code openssl rand -base64 32}
   *
   * <p><b>application.yml 示例：</b>
   *
   * <pre>
   * ydsz:
   *   userinfo:
   *     mfa:
   *       encryption-key: ${MFA_ENCRYPTION_KEY:}
   * </pre>
   */
  private String mfaEncryptionKey;

  /**
   * 分端会话限制配置。
   *
   * <p>按设备类型独立限制会话数，-1 表示不限制。未配置分端限制时回退到全局 {@link #maxSessionsPerUser}。
   * 分端限制是全局限制的子集：先满足分端限制，再满足全局限制。
   *
   * <p><b>application.yml 示例：</b>
   *
   * <pre>
   * ydsz:
   *   userinfo:
   *     max-sessions-per-device-type:
   *       web: 3
   *       app: 2
   *       api: -1
   * </pre>
   */
  private Map<String, Integer> maxSessionsPerDeviceType = new HashMap<>(16);

  /**
   * 获取指定设备类型的最大会话数。
   *
   * <p>未配置分端限制时回退到全局 {@link #getMaxSessionsPerUser()}，保持向后兼容。
   * -1 表示不限制。
   *
   * @param deviceType 设备类型编码（如 web/app/api）
   * @return 该设备类型的最大会话数
   */
  public int getMaxSessionsForDevice(String deviceType) {
    return maxSessionsPerDeviceType.getOrDefault(deviceType, getMaxSessionsPerUser());
  }

  // ==================== P1-1 登录风控配置 ====================

  /** 风险评分：IP 风险权重（默认 30）。 */
  private int riskIpWeight = DEFAULT_RISK_IP_WEIGHT;

  /** 风险评分：时间异常权重（默认 20）。 */
  private int riskTimeWeight = DEFAULT_RISK_TIME_WEIGHT;

  /** 风险评分：设备异常权重（默认 25）。 */
  private int riskDeviceWeight = DEFAULT_RISK_DEVICE_WEIGHT;

  /** 风险评分：频率异常权重（默认 25）。 */
  private int riskFrequencyWeight = DEFAULT_RISK_FREQUENCY_WEIGHT;

  /** 风险评分：异常时段起始小时（默认 0，即凌晨 0 点）。 */
  private int riskAnomalyStartHour = DEFAULT_RISK_ANOMALY_START_HOUR;

  /** 风险评分：异常时段结束小时（默认 6，即凌晨 6 点）。 */
  private int riskAnomalyEndHour = DEFAULT_RISK_ANOMALY_END_HOUR;

  /** 风险评分：频率异常窗口（分钟，默认 5 分钟）。 */
  private int riskFrequencyWindowMinutes = DEFAULT_RISK_FREQUENCY_WINDOW_MINUTES;

  /** 风险评分：频率异常阈值（窗口内尝试次数，默认 3 次）。 */
  private int riskFrequencyThreshold = DEFAULT_RISK_FREQUENCY_THRESHOLD;

  /**
   * P2-6: 可信代理 IP 列表。
   *
   * <p>仅当请求来源（remoteAddr）命中此列表时，才信任 {@code X-Forwarded-For} 等代理头；
   * 列表为空（默认）时不读取任何代理头，直接用 remoteAddr，防止客户端伪造 IP 绕过登录风控。
   */
  private List<String> trustedProxies = List.of();

  // ==================== P1-2 Token 自动续签配置 ====================

  /**
   * P1-2: 是否启用 Token 自动续签。
   *
   * <p>开启后，当 access_token 剩余有效期低于阈值时，自动签发新 Token 并在响应头 {@code X-Access-Token} 中返回。
   * 前端检测到该响应头后替换本地存储的 Token，实现无感续期。
   */
  private boolean tokenAutoRenewalEnabled = true;

  /**
   * P1-2: Token 自动续签阈值百分比（0-100）。
   *
   * <p>当 access_token 剩余有效期 ＜ TTL × thresholdPercent / 100 时触发续签。
   * 默认 10%（即 TTL 7200 秒时，剩余 720 秒触发续签）。
   */
  private int tokenAutoRenewalThresholdPercent = DEFAULT_TOKEN_AUTO_RENEWAL_THRESHOLD_PERCENT;

  // ==================== P1-3 路径排除配置 ====================

  /**
   * P1-3: 不需要鉴权的路径列表（Ant 风格）。
   *
   * <p>对标 XXL-SSO 的路径排除能力，用于排除健康检查、Swagger、actuator 等无需鉴权的路径。
   * 排除的路径不会经过 Spring Security 的认证过滤器链。
   */
  private List<String> authExcludePaths = List.of(
      "/actuator/**",
      "/swagger-ui/**",
      "/v3/api-docs/**",
      "/favicon.ico");

  // ==================== 安全告警配置 ====================

  /** 安全告警：告警去重时间窗口（秒），默认 300 秒（5 分钟） */
  private long alertDedupTtlSeconds = DEFAULT_ALERT_DEDUP_TTL_SECONDS;

  /** 安全告警：IP 维度告警去重时间窗口（秒），默认 180 秒（3 分钟） */
  private long alertIpDedupTtlSeconds = DEFAULT_ALERT_IP_DEDUP_TTL_SECONDS;

  /** 安全告警：暴力破解检测阈值（同一 IP 5 分钟内失败次数），默认 10 次 */
  private int alertBruteForceThreshold = DEFAULT_ALERT_BRUTE_FORCE_THRESHOLD;

  /** 安全告警：密码喷洒检测阈值（同一 IP 尝试不同用户数），默认 5 个 */
  private int alertPasswordSprayThreshold = DEFAULT_ALERT_PASSWORD_SPRAY_THRESHOLD;

  /**
   * OAuth2 客户端配置
   *
   * <p>由 {@link com.njydsz.userinfo.web.controller.OAuth2Controller} 在 {@code /authorize} 和 {@code
   * /token} 端点校验 clientId / clientSecret / redirectUri。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  @Data
  public static class OAuth2Client {
    /** 客户端名称（显示在用户授权同意界面上） */
    private String clientName;

    /** 客户端密钥：与 clientId 配对，在 /token 端点强制校验，建议存储在密钥管理服务 */
    private String clientSecret;

    /** 允许的回调地址白名单（RFC 6749 §3.1.2.3）：防止开放重定向攻击 */
    private List<String> redirectUris;

    /**
     * P1-3: 客户端允许申请的 scope 集合（如 read / write / openid）。
     *
     * <p>为 null 或空时表示不限制（兼容存量客户端）；配置后 /authorize 与 /token
     * 仅返回白名单内的 scope，实现细粒度授权。
     */
    private Set<String> allowedScopes;

    /**
     * P1-3: 客户端允许的 audience（资源标识）集合。
     *
     * <p>用于 JWT 中 {@code aud} claim 的声明，标识该 token 可访问的资源服务。
     */
    private Set<String> allowedAudiences;
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
