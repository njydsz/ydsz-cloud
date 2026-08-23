package com.njydsz.userinfo.server.auth;

import java.security.MessageDigest;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.util.security.DigestUtils;

/**
 * API 参数签名工具类（P0-7）。
 *
 * <p>提供基于 HMAC-SHA256 的请求参数签名生成与验证能力，用于 {@code /api/internal/**}
 * 端点的零信任内部调用认证。签名覆盖 HTTP 方法、路径、查询参数、请求体、时间戳和 Nonce，
 * 确保请求在传输过程中未被篡改。
 *
 * <p><b>签名格式：</b>
 *
 * <pre>
 *   signContent = method + "\n" + path + "\n" + query + "\n" + body + "\n" + timestamp + "\n" + nonce
 *   signature   = Base64(HmacSHA256(signContent, secret))
 * </pre>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>签名比较使用 {@link MessageDigest#isEqual} 防止时序攻击
 *   <li>时间戳校验防止过期请求重放
 *   <li>Nonce 唯一性校验（配合 Redis SETNX）防止请求重放
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.web.filter.ApiSignatureFilter 签名校验过滤器
 * @see com.njydsz.userinfo.server.config.ApiSignatureProperties 签名配置
 */
@Slf4j
public final class ApiSignatureUtil {

  /** 签名字段分隔符 */
  private static final char FIELD_SEPARATOR = '\n';

  /** 私有构造器，工具类不允许实例化 */
  private ApiSignatureUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 生成请求签名。
   *
   * <p>将请求要素拼接为签名字符串后，使用 HmacSHA256 计算摘要并 Base64 编码输出。
   *
   * @param request 签名请求要素
   * @param secret 签名密钥
   * @return Base64 编码的签名值；参数不合法时返回 null
   */
  public static String sign(ApiSignRequest request, String secret) {
    String content = buildSignContent(request);
    if (content == null) {
      return null;
    }
    return DigestUtils.hmacSha256Base64(content, secret);
  }

  /**
   * 构建签名字符串。
   *
   * <p>按固定顺序拼接请求要素，以 {@code \n} 分隔：
   *
   * <pre>
   *   method\npath\nquery\nbody\ntimestamp\nnonce
   * </pre>
   *
   * @param request 签名请求要素
   * @return 签名字符串；method/path/nonce 任一为空时返回 null
   */
  public static String buildSignContent(ApiSignRequest request) {
    if (request.method() == null || request.method().isBlank()) {
      return null;
    }
    if (request.path() == null || request.path().isBlank()) {
      return null;
    }
    if (request.nonce() == null || request.nonce().isBlank()) {
      return null;
    }
    // query 和 body 为空时使用空字符串，不使用 null
    String safeQuery = request.query() != null ? request.query() : "";
    String safeBody = request.body() != null ? request.body() : "";
    return request.method() + FIELD_SEPARATOR
        + request.path() + FIELD_SEPARATOR
        + safeQuery + FIELD_SEPARATOR
        + safeBody + FIELD_SEPARATOR
        + request.timestamp() + FIELD_SEPARATOR
        + request.nonce();
  }

  /**
   * 验证请求签名。
   *
   * <p>使用 {@link MessageDigest#isEqual} 进行恒定时间比较，防止时序攻击。
   *
   * @param signature 待验证的签名值（Base64 编码）
   * @param request 签名请求要素
   * @param secret 签名密钥
   * @return true 表示签名有效
   */
  public static boolean verify(String signature, ApiSignRequest request, String secret) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    if (secret == null || secret.isBlank()) {
      return false;
    }
    String computed = sign(request, secret);
    if (computed == null) {
      return false;
    }
    return DigestUtils.constantTimeEquals(computed, signature);
  }

  /**
  * 检查签名时间戳是否已过期。
  *
  * @param timestamp 签名时间戳（毫秒 Unix epoch）
  * @param ttlMillis 签名有效期（毫秒）
  * @return true 表示已过期
  */
  public static boolean isExpired(long timestamp, long ttlMillis) {
    long now = System.currentTimeMillis();
    return Math.abs(now - timestamp) > ttlMillis;
  }

  /**
   * 生成随机 Nonce（UUID 去横线）。
   *
   * <p>生成的 Nonce 长度为 32 字符，用于配合 Redis SETNX 实现请求防重放。
   *
   * @return 随机 Nonce 字符串
   */
  public static String generateNonce() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
