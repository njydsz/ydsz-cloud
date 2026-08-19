package com.njydsz.userinfo.server.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 参数签名配置属性（P0-7）。
 *
 * <p>为 {@code /api/internal/**} 端点提供零信任内部调用能力：通过 HMAC-SHA256 参数签名
 * 替代原有的 IP 白名单/IP 标记头机制，实现防篡改、防重放、调用方身份认证。
 *
 * <p><b>签名算法：</b>
 *
 * <pre>
 *   signContent = method + "\n" + path + "\n" + query + "\n" + body + "\n" + timestamp + "\n" + nonce
 *   signature = Base64(HmacSHA256(signContent, secret))
 * </pre>
 *
 * <p><b>请求头要求：</b>
 *
 * <ul>
 *   <li>{@code X-Timestamp} — 签名时间戳（毫秒 Unix epoch）
 *   <li>{@code X-Nonce} — 一次性随机字符串（防重放）
 *   <li>{@code X-Signature} — Base64 编码的 HMAC-SHA256 签名值
 * </ul>
 *
 * <p><b>配置示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     api-signature:
 *       enabled: true
 *       secret: ${API_SIGNATURE_SECRET:default-change-me}
 *       algorithm: HmacSHA256
 *       header-timestamp: X-Timestamp
 *       header-nonce: X-Nonce
 *       header-signature: X-Signature
 *       ttl-millis: 300000
 *       exclude-paths:
 *         - /api/internal/health
 * </pre>
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see com.njydsz.userinfo.web.filter.ApiSignatureFilter 签名校验过滤器
 * @see com.njydsz.userinfo.server.auth.ApiSignatureUtil 签名工具类
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.api-signature")
public class ApiSignatureProperties {

  /** 默认签名算法 */
  private static final String DEFAULT_ALGORITHM = "HmacSHA256";

  /** 默认时间戳请求头名称 */
  private static final String DEFAULT_HEADER_TIMESTAMP = "X-Timestamp";

  /** 默认 Nonce 请求头名称 */
  private static final String DEFAULT_HEADER_NONCE = "X-Nonce";

  /** 默认签名请求头名称 */
  private static final String DEFAULT_HEADER_SIGNATURE = "X-Signature";

  /** 默认签名有效期（毫秒），5 分钟 */
  private static final long DEFAULT_TTL_MILLIS = 300000L;

  /** 是否启用 API 参数签名校验（默认 true） */
  private boolean enabled = true;

  /** 签名密钥（建议通过环境变量注入） */
  private String secret;

  /** 签名算法（默认 HmacSHA256） */
  private String algorithm = DEFAULT_ALGORITHM;

  /** 时间戳请求头名称 */
  private String headerTimestamp = DEFAULT_HEADER_TIMESTAMP;

  /** Nonce 请求头名称 */
  private String headerNonce = DEFAULT_HEADER_NONCE;

  /** 签名请求头名称 */
  private String headerSignature = DEFAULT_HEADER_SIGNATURE;

  /** 签名有效期（毫秒），超过该时间窗口的请求视为过期 */
  private long ttlMillis = DEFAULT_TTL_MILLIS;

  /** 不需要签名的路径列表 */
  private List<String> excludePaths = new ArrayList<>();
}
