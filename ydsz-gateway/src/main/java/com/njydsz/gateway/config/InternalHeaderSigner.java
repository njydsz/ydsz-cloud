package com.njydsz.gateway.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.njydsz.common.util.security.HexUtils;

/**
 * 内部请求头签名工具。
 *
 * <p>使用 HMAC-SHA256 对网关注入的内部头进行签名，防止客户端伪造。下游服务可使用相同密钥验证签名。
 *
 * <p>签名 payload 拼接顺序：traceId|userId|username|roles|permissions
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class InternalHeaderSigner {

  private static final String HMAC_SHA256 = "HmacSHA256";

  private InternalHeaderSigner() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 验证内部头签名（供下游服务调用）。
   *
   * <p>用相同密钥重新计算 HMAC-SHA256，对比签名是否一致（恒定时间比较）。
   *
   * @param secret 签名密钥（与网关相同）
   * @param traceId 链路追踪 ID
   * @param userId 用户 ID
   * @param username 用户名
   * @param roles 角色（CSV）
   * @param permissions 权限（CSV）
   * @param receivedSig 收到的签名
   * @return true=签名有效；false=签名无效
   */
  public static boolean verify(
      String secret,
      String traceId,
      String userId,
      String username,
      String roles,
      String permissions,
      String receivedSig) {
    String expectedSig = sign(secret, traceId, userId, username, roles, permissions);
    return slowEquals(expectedSig, receivedSig);
  }

  /**
   * 生成内部头签名。
   *
   * <p>payload 拼接顺序：traceId|userId|username|roles|permissions
   *
   * @param secret 签名密钥
   * @param traceId 链路追踪 ID
   * @param userId 用户 ID
   * @param username 用户名
   * @param roles 角色（CSV）
   * @param permissions 权限（CSV）
   * @return HMAC-SHA256 签名（十六进制）
   */
  public static String sign(
      String secret,
      String traceId,
      String userId,
      String username,
      String roles,
      String permissions) {
    String payload =
        String.join("|",
            traceId != null ? traceId : "",
            userId != null ? userId : "",
            username != null ? username : "",
            roles != null ? roles : "",
            permissions != null ? permissions : "");
    return hmacSha256(secret, payload);
  }

  /**
   * 恒定时间比较（防计时攻击）。
   *
   * @param a 字符串 A
   * @param b 字符串 B
   * @return true=完全相等；false=不相等
   */
  private static boolean slowEquals(String a, String b) {
    if (a == null || b == null || a.length() != b.length()) {
      return false;
    }
    int diff = 0;
    for (int i = 0; i < a.length(); i++) {
      diff |= a.charAt(i) ^ b.charAt(i);
    }
    return diff == 0;
  }

  /**
   * 计算 HMAC-SHA256。
   *
   * @param secret 签名密钥
   * @param payload 待签名内容
   * @return 十六进制签名串
   */
  private static String hmacSha256(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
      mac.init(keySpec);
      byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return HexUtils.encode(hmacBytes);
    } catch (Exception e) {
      throw new IllegalStateException("生成内部头签名失败", e);
    }
  }
}
