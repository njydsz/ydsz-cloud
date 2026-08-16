package com.njydsz.common.notify.signature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 企业微信回调签名验证工具。
 *
 * <p>P0-2: 从 workflow 模块迁移到 common-notify，作为通用 IM 签名能力。 供 workflow 三方审批回调验证、common-notify
 * WeComNotifySender 等场景共用。
 *
 * <p>算法：SHA1(sort(token, timestamp, nonce, encrypt))，结果以十六进制小写编码后与回调签名比对。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class WeComSignatureUtil {

  private static final Logger LOG = LoggerFactory.getLogger(WeComSignatureUtil.class);

  private static final String SHA_1 = "SHA-1";

  private WeComSignatureUtil() {}

  /**
   * 验证企微回调签名
   *
   * @param token 回调配置的 Token
   * @param timestamp 时间戳
   * @param nonce 随机串
   * @param encrypt 加密载荷
   * @param signature 回调签名（十六进制）
   * @return 签名校验通过返回 true，否则 false
   */
  public static boolean verifySignature(
      String token, String timestamp, String nonce, String encrypt, String signature) {
    if (signature == null || signature.isEmpty() || token == null) {
      return false;
    }
    try {
      String[] arr = new String[] {token, str(timestamp), str(nonce), str(encrypt)};
      Arrays.sort(arr);
      StringBuilder sb = new StringBuilder();
      for (String s : arr) {
        sb.append(s);
      }
      // 注意：此处保留手写 MessageDigest 实现。
      // 企微回调签名算法规定使用 SHA-1，而 DigestUtils 仅提供 sha256Hex / sha512Hex /
      // hmacSha256Hex 等便捷方法，未提供 SHA-1 的 Hex 便捷方法，因此无法委托 DigestUtils。
      MessageDigest md = MessageDigest.getInstance(SHA_1);
      byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      String computed = toHexLower(digest);
      return constantTimeEquals(computed, signature.toLowerCase());
    } catch (Exception e) {
      LOG.warn("[WeComSignatureUtil] 签名验证异常 timestamp={}: {}", timestamp, e.getMessage(), e);
      return false;
    }
  }

  private static String str(String s) {
    return s == null ? "" : s;
  }

  private static String toHexLower(byte[] bytes) {
    char[] hex = "0123456789abcdef".toCharArray();
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(hex[(b >> 4) & 0x0F]);
      sb.append(hex[b & 0x0F]);
    }
    return sb.toString();
  }

  /** 常量时间字符串比较，避免时序攻击 */
  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    if (a.length() != b.length()) {
      return false;
    }
    int r = 0;
    for (int i = 0; i < a.length(); i++) {
      r |= a.charAt(i) ^ b.charAt(i);
    }
    return r == 0;
  }
}
