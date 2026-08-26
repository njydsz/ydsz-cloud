package com.njydsz.common.notify.signature;

import com.njydsz.common.util.security.DigestUtils;

/**
 * IM 平台回调签名验证工具。
 *
 * <p>P0-2: 从 workflow 模块迁移到 common-notify，作为通用 IM 签名能力。 供 workflow 三方审批回调验证、common-notify
 * DingTalkNotifySender 等场景共用。
 *
 * <p>算法：HmacSHA256，密钥为 appSecret，签名内容为 timestamp + nonce + encrypt， 计算结果经 Base64 编码后与回调签名比对。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class DingTalkSignatureUtil {

  private DingTalkSignatureUtil() {}

  /**
   * 验证 IM 平台回调签名
   *
   * @param timestamp 时间戳
   * @param nonce 随机串
   * @param encrypt 加密载荷
   * @param signature 回调签名（Base64）
   * @param appSecret 应用 appSecret（作为 HmacSHA256 密钥）
   * @return 签名校验通过返回 true，否则 false
   */
  public static boolean verifySignature(
      String timestamp, String nonce, String encrypt, String signature, String appSecret) {
    if (signature == null || signature.isEmpty() || appSecret == null || appSecret.isEmpty()) {
      return false;
    }
    String data = str(timestamp) + str(nonce) + str(encrypt);
    return DigestUtils.verifySignature(
        data, appSecret, signature, DigestUtils.SignatureEncoding.BASE64);
  }

  private static String str(String s) {
    return s == null ? "" : s;
  }
}
