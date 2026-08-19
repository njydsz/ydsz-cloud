package com.njydsz.userinfo.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

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
 * @since 1.6.0
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
   * @param method    HTTP 方法（GET/POST/PUT/DELETE 等）
   * @param path      请求路径（如 /api/internal/user/info）
   * @param query     查询字符串（为空时使用空字符串，不使用 null）
   * @param body      请求体（为空时使用空字符串，不使用 null）
   * @param timestamp 签名时间戳（毫秒 Unix epoch）
   * @param nonce     一次性随机字符串
   * @param secret    签名密钥
   * @return Base64 编码的签名值；参数不合法时返回 null
   */
  public static String sign(
      String method,
      String path,
      String query,
      String body,
      long timestamp,
      String nonce,
      String secret) {
    String content = buildSignContent(method, path, query, body, timestamp, nonce);
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
   * @param method    HTTP 方法
   * @param path      请求路径
   * @param query     查询字符串（null 视为空字符串）
   * @param body      请求体（null 视为空字符串）
   * @param timestamp 签名时间戳
   * @param nonce     一次性随机字符串
   * @return 签名字符串；method/path/nonce/secret 任一为空时返回 null
   */
  public static String buildSignContent(
      String method,
      String path,
      String query,
      String body,
      long timestamp,
      String nonce) {
    if (method == null || method.isBlank()) {
      return null;
    }
    if (path == null || path.isBlank()) {
      return null;
    }
    if (nonce == null || nonce.isBlank()) {
      return null;
    }
    // query 和 body 为空时使用空字符串，不使用 null
    String safeQuery = query != null ? query : "";
    String safeBody = body != null ? body : "";
    return method + FIELD_SEPARATOR
        + path + FIELD_SEPARATOR
        + safeQuery + FIELD_SEPARATOR
        + safeBody + FIELD_SEPARATOR
        + timestamp + FIELD_SEPARATOR
        + nonce;
  }

  /**
   * 验证请求签名。
   *
   * <p>使用 {@link MessageDigest#isEqual} 进行恒定时间比较，防止时序攻击。
   *
   * @param signature 待验证的签名值（Base64 编码）
   * @param method    HTTP 方法
   * @param path      请求路径
   * @param query     查询字符串
   * @param body      请求体
   * @param timestamp 签名时间戳
   * @param nonce     一次性随机字符串
   * @param secret    签名密钥
   * @return true 表示签名有效
   */
  public static boolean verify(
      String signature,
      String method,
      String path,
      String query,
      String body,
      long timestamp,
      String nonce,
      String secret) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    if (secret == null || secret.isBlank()) {
      return false;
    }
    String computed = sign(method, path, query, body, timestamp, nonce, secret);
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
    return java.util.UUID.randomUUID().toString().replace("-", "");
  }
}
