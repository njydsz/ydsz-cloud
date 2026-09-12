package com.njydsz.common.notify.signature;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.util.security.DigestUtils;

/**
 * 钉钉群机器人 Webhook 加签工具。
 *
 * <p>实现钉钉官方加签协议：{@code timestamp + "\n" + secret} 经 HMAC-SHA256 计算、
 * Base64 编码并 URL Encode 后，与 {@code timestamp} 一并拼接到 Webhook URL。</p>
 *
 * <p><b>约束：</b>本工具是仓内唯一的钉钉加签实现，业务侧（如告警通知渠道）
 * 应直接复用本方法，禁止各自手写 HMAC 加签逻辑。</p>
 *
 * <p>本类无状态，线程安全。</p>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DingTalkSignUtil {

  private static final Logger LOG = LoggerFactory.getLogger(DingTalkSignUtil.class);

  private DingTalkSignUtil() {
    throw new UnsupportedOperationException("DingTalkSignUtil is a utility class");
  }

  /**
   * 构建带加签参数的钉钉 Webhook URL。
   *
   * <p>secret 为空时原样返回；URL 已含查询参数时以 {@code &} 拼接，否则以 {@code ?} 拼接；
   * 签名失败时记录 warn 日志并返回原始 URL（不阻断发送主流程）。</p>
   *
   * @param url 原始 Webhook URL，不可为空
   * @param secret 加签密钥（钉钉安全设置"加签"生成的密钥），可为空表示不加签
   * @return 拼接 {@code timestamp} 与 {@code sign} 参数后的 URL；签名为空或失败时返回原始 URL
   */
  public static String signWebhookUrl(String url, String secret) {
    if (url == null || url.isEmpty()) {
      return url;
    }
    if (secret == null || secret.isEmpty()) {
      return url;
    }
    long timestamp = System.currentTimeMillis();
    try {
      String sign =
          URLEncoder.encode(
              DigestUtils.hmacSha256Base64(timestamp + "\n" + secret, secret),
              StandardCharsets.UTF_8);
      return url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
    } catch (Exception e) {
      LOG.warn("钉钉 webhook 签名失败: {}", e.getMessage());
      return url;
    }
  }
}
