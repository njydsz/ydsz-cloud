package com.njydsz.common.feign.signature;

import java.security.SecureRandom;

import com.njydsz.common.util.security.DigestUtils;

/**
 * 签名工具类（开放平台场景预留）。
 *
 * <p>提供 HMAC-SHA256 签名和 SHA-256 哈希计算。所有方法底层委托
 * {@link DigestUtils}（YDIZ-COMMON-054）。
 *
 * <p><b>预留说明：</b>当前为工具类预留，未在拦截器中实际调用。 启用时由 {@code SignatureRequestInterceptor} 调用生成请求签名。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class SignatureUtils {

  private SignatureUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * HMAC-SHA256 签名。
   *
   * <p>YDIZ-COMMON-054: 委托 {@link DigestUtils#hmacSha256Base64}。
   *
   * @param data 待签名字符串
   * @param secret 密钥
   * @return Base64 编码的签名字符串
   */
  public static String hmacSha256(String data, String secret) {
    return DigestUtils.hmacSha256Base64(data, secret);
  }

  /**
   * SHA-256 哈希。
   *
   * <p>YDIZ-COMMON-054: 委托 {@link DigestUtils#sha256Hex}。
   *
   * @param data 待哈希字符串
   * @return 十六进制哈希字符串（小写）
   */
  public static String sha256Hex(String data) {
    return DigestUtils.sha256Hex(data);
  }

  /**
   * 生成随机 Nonce（32 字符十六进制）。
   *
   * @return 随机 nonce
   */
  public static String generateNonce() {
    byte[] nonceBytes = new byte[16];
    SecureRandom random = new SecureRandom();
    random.nextBytes(nonceBytes);
    return DigestUtils.sha256Hex(nonceBytes).substring(0, 32);
  }
}
