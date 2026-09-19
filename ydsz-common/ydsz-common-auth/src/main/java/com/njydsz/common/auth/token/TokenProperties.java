package com.njydsz.common.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token 配置属性
 *
 * <p>绑定前缀 ydsz.auth.token.*，控制 JWT 鉴权体系的核心参数：密钥、有效期、签发者等信息。
 *
 * <p>支持的 Token 类型：
 * <ul>
 *   <li>Access Token — 短期访问凭证，默认 2 小时</li>
 *   <li>Refresh Token — 长期刷新凭证，默认 7 天，用于续期 Access Token</li>
 *   <li>ID Token — 身份标识 Token，默认 10 分钟，用于 OAuth 场景</li>
 * </ul>
 *
 * <p>启动时由 {@link TokenProperties#validate()} 校验 secretKey 是否已配置且长度满足 ≥32 字节，
 * 不满足将直接抛出 IllegalStateException 阻止服务启动。若检测到弱密钥（如包含 "change-me"、"default"、"secret"、"123456"），
 * 将输出醒目的 WARN 日志提醒更换。
 *
 * <p>配置模式：
 * <pre>
 * ydsz.auth.token:
 *   secret-key: "your-256-bit-secret-key-at-least-32-bytes"
 *   access-token-expire-seconds: 7200
 *   refresh-token-expire-seconds: 604800
 *   issuer: "ydsz-auth"
 * </pre>
 *
 * <p>生成推荐密钥可使用 {@link #generateSecureSecret()} 静态工具方法。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.auth.token")
public class TokenProperties {

  private static final Logger LOG = LoggerFactory.getLogger(TokenProperties.class);

  /** 弱密钥模式：密钥包含以下子串将被判定为弱密钥并触发启动告警。 */
  private static final String[] WEAK_SECRET_PATTERNS =
      new String[] {"change-me", "changeme", "default", "secret", "123456", "password", "admin", "test", "ydsz"};

  /** 是否启用 Token 服务，默认 true */
  private boolean enabled = true;

  /**
   * JWT 签名密钥（HMAC-SHA256）
   *
   * <p>生产环境必须配置，建议使用 256 位（32 字节）以上的随机字符串
   */
  private String secretKey;

  /** Access Token 有效期（秒），默认 2 小时 */
  private long accessTokenExpireSeconds = 7200;

  /** Refresh Token 有效期（秒），默认 7 天 */
  private long refreshTokenExpireSeconds = 604800;

  /** Token 签发者（issuer） */
  private String issuer = "ydsz-common";

  /** Token 主题（subject） */
  private String subject = "ydsz-user";

  /**
   * Token 受众（audience）— P1: 防止跨服务令牌重用
   *
   * <p>当 token 颁发给网关消费时，audience 应为 "ydsz-gateway"； 下游服务（如 userinfo/finance）解析时校验 aud 必须匹配， 防止
   * token 被错误地用在其它服务上。
   *
   * <p>留空（默认）时不强制校验 aud，保持向后兼容； 配置后会在签发时写入 aud，在解析时强制 require(aud)。
   */
  private String audience;

  /** ID Token 有效期（秒），默认 10 分钟 */
  private long idTokenExpireSeconds = 600;

  /** 是否启用 JWKS 公钥端点，默认 false */
  private boolean jwksEnabled = false;

  /**
   * RSA 公钥 PEM 格式字符串（可选）
   *
   * <p>配置后 JWKS 端点将返回 RSA 公钥（kty=RSA）； 未配置时返回 HMAC 对称密钥（kty=oct）。
   */
  private String publicKeyPem;

  /**
   * 校验密钥配置
   *
   * <p>启动时检查 secretKey 是否已配置且长度 >= 32 字节
   *
   * <p>若检测到弱密钥（密钥包含常见弱模式如 "change-me"、"default"、"secret" 等）， 将输出醒目 WARN 日志，提醒在生产环境更换为安全的随机密钥。 可通过 {@link #generateSecureSecret()} 快速生成强密钥。
   *
   * @throws IllegalStateException 若密钥未配置或长度不足
   */
  @PostConstruct
  public void validate() {
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
          "JWT secretKey 未配置，请在配置文件中设置 ydsz.auth.token.secret-key（建议 32 字节以上的随机字符串）");
    }
    if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
          "JWT secretKey 长度不足 32 字节，当前长度: "
              + secretKey.getBytes(StandardCharsets.UTF_8).length
              + "，请使用更安全的密钥");
    }
    // 弱密钥检测：启动时提醒更换
    String lowerKey = secretKey.toLowerCase();
    for (String weakPattern : WEAK_SECRET_PATTERNS) {
      if (lowerKey.contains(weakPattern)) {
        LOG.warn("╔══════════════════════════════════════════════════════════════════╗");
        LOG.warn("║  ⚠  JWT SecretKey 疑似弱密钥，生产环境必须更换！                 ║");
        LOG.warn("║  当前密钥包含弱模式: \"{}\"，建议立即替换为高熵随机字符串          ║", weakPattern);
        LOG.warn("║  推荐生成方式: TokenProperties.generateSecureSecret()            ║");
        LOG.warn("╚══════════════════════════════════════════════════════════════════╝");
        break;
      }
    }
  }

  /**
   * 生成安全的 JWT HMAC-SHA256 签名密钥。
   *
   * <p>使用 {@link SecureRandom} 高强度随机数生成 32 字节（256 位）熵源， 经 Base64 URL 安全编码后返回可直接配置到 {@code
   * ydsz.auth.token.secret-key} 的字符串。
   *
   * <p>推荐在运维初始化脚本中调用：
   *
   * <pre>{@code
   * // 在 main 方法或运维工具中调用
   * String secret = TokenProperties.generateSecureSecret();
   * System.out.println("推荐 secret-key: " + secret);
   * }</pre>
   *
   * @return Base64 URL 安全编码的 256 位随机密钥字符串
   */
  public static String generateSecureSecret() {
    byte[] raw = new byte[32];
    SecureRandom secureRandom = new SecureRandom();
    secureRandom.nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }
