package com.njydsz.message.server.channel.push;

import com.njydsz.common.util.security.DigestUtils;

/**
 * 个推（GeTui）V2 API 签名工具。
 *
 * <p>签名算法：{@code SHA-256(appKey + timestamp + masterSecret)} 的十六进制小写串， 委托给 {@link
 * DigestUtils#sha256Hex(String)} 统一实现。 纯静态方法，可独立单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class GetuiPushSigner {

  private GetuiPushSigner() {}

  /**
   * 计算个推鉴权签名。
   *
   * @param appKey 个推 AppKey
   * @param timestamp 时间戳（毫秒）
   * @param masterSecret MasterSecret
   * @return SHA-256 十六进制签名
   */
  public static String sign(String appKey, String timestamp, String masterSecret) {
    String raw = appKey + timestamp + masterSecret;
    return DigestUtils.sha256Hex(raw);
  }
}
