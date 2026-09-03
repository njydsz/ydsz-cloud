package com.njydsz.userinfo.server.config;
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
  private Map<String, OAuth2Client> oauth2Clients = new HashMap<>(16);