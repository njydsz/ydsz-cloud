package com.njydsz.common.feign.signature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 签名工具类（开放平台场景预留）。
 *
 * <p>提供 HMAC-SHA256 签名和 SHA-256 哈希计算。
 *
 * <p><b>预留说明：</b>当前为工具类预留，未在拦截器中实际调用。 启用时由 {@code SignatureRequestInterceptor} 调用生成请求签名。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class SignatureUtils {

  /** HMAC-SHA256 算法名 */
  public static final String HMAC_SHA256 = "HmacSHA256";

  /** SHA-256 算法名 */
  public static final String SHA256 = "SHA-256";

  private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

  private SignatureUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * HMAC-SHA256 签名。
   *
   * @param data 待签名字符串
   * @param secret 密钥
   * @return Base64 编码的签名字符串
   */
  public static String hmacSha256(String data, String secret) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      SecretKeySpec secretKeySpec =
          new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
      mac.init(secretKeySpec);
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 签名失败: " + e.getMessage(), e);
    }
  }

  /**
   * SHA-256 哈希。
   *
   * @param data 待哈希字符串
   * @return 十六进制哈希字符串（小写）
   */
  public static String sha256Hex(String data) {
    try {
      MessageDigest digest = MessageDigest.getInstance(SHA256);
      byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 哈希失败: " + e.getMessage(), e);
    }
  }

  /**
   * 字节数组转十六进制字符串。
   *
   * @param bytes 字节数组
   * @return 十六进制字符串（小写）
   */
  public static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(HEX_CHARS[(b >> 4) & 0x0F]);
      sb.append(HEX_CHARS[b & 0x0F]);
    }
    return sb.toString();
  }

  /**
   * 生成随机 Nonce（32 字符十六进制）。
   *
   * @return 随机 nonce
   */
  public static String generateNonce() {
    byte[] nonceBytes = new byte[16];
    java.security.SecureRandom random = new java.security.SecureRandom();
    random.nextBytes(nonceBytes);
    return bytesToHex(nonceBytes);
  }
}
