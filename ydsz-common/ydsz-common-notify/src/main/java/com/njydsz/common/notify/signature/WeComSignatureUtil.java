package com.njydsz.common.notify.signature;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.njydsz.common.util.security.DigestUtils;

/**
 * 企业微信回调签名验证工具。
 *
 * <p>P0-2: 从 workflow 模块迁移到 common-notify，作为通用 IM 签名能力。 供 workflow 三方审批回调验证、common-notify
 * WeComNotifySender 等场景共用。
 *
 * <p>算法：SHA1(sort(token, timestamp, nonce, encrypt))，结果以十六进制小写编码后与回调签名比对。
 *
 * <p>底层委托 {@link DigestUtils#sha1Hex}（YDIZ-COMMON-054）+ {@link DigestUtils#constantTimeEquals}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class WeComSignatureUtil {

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
      // YDIZ-COMMON-054: 委托 DigestUtils.sha1Hex 计算 SHA-1 散列
      String computed = DigestUtils.sha1Hex(sb.toString());
      return DigestUtils.constantTimeEquals(computed, signature.toLowerCase());
    } catch (Exception e) {
      return false;
    }
  }

  private static String str(String s) {
    return s == null ? "" : s;
  }
}
