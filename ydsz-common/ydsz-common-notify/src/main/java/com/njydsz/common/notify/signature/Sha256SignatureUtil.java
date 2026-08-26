package com.njydsz.common.notify.signature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.util.security.DigestUtils;

/**
 * IM 平台回调签名验证工具（SHA256 算法）。
 *
 * <p>P0-2: 从 workflow 模块迁移到 common-notify，作为通用 IM 签名能力。 供 workflow 三方审批回调验证、common-notify
 * 通知Sender 等场景共用。
 *
 * <p>算法：SHA256(timestamp + nonce + encrypt + appSecret)，结果以十六进制小写编码后与回调签名比对。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class Sha256SignatureUtil {

  private static final Logger LOG = LoggerFactory.getLogger(Sha256SignatureUtil.class);

  private Sha256SignatureUtil() {}

  /**
   * 验证 IM 平台回调签名
   *
   * @param timestamp 时间戳
   * @param nonce 随机串
   * @param encrypt 加密载荷
   * @param signature 回调签名（十六进制）
   * @param appSecret 应用 appSecret
   * @return 签名校验通过返回 true，否则 false
   */
  public static boolean verifySignature(
      String timestamp, String nonce, String encrypt, String signature, String appSecret) {
    if (signature == null || signature.isEmpty() || appSecret == null || appSecret.isEmpty()) {
      return false;
    }
    try {
      String data = str(timestamp) + str(nonce) + str(encrypt) + appSecret;
      String computed = DigestUtils.sha256Hex(data);
      return constantTimeEquals(computed, signature.toLowerCase());
    } catch (Exception e) {
      LOG.warn("[Sha256SignatureUtil] 签名验证异常 timestamp={}: {}", timestamp, e.getMessage(), e);
      return false;
    }
  }

  private static String str(String s) {
    return s == null ? "" : s;
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
