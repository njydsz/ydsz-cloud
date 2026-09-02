package com.njydsz.message.server.token;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.message.server.config.MessageProperties;

/**
 * 退订 token 工具（P1-5）。
 *
 * <p>基于 HMAC-SHA256 签名的无状态 token，格式：
 *
 * <pre>{@code
 * base64url(payload) + "." + base64url(hmac_sha256(payload, secret))
 * }</pre>
 *
 * <p>payload 为 {@code userId|topicCode|channel|expiresAtEpochSecond} 的明文， 用 {@code |} 分隔。token
 * 不加密（仅签名），因为退订链接不携带敏感信息， 但不可篡改（修改任一字段会导致签名校验失败）。
 *
 * <p>设计权衡：
 *
 * <ul>
 *   <li>无状态：无需 Redis 持久化 token，token 自带过期时间，签名验证即可
 *   <li>不可撤销：一旦发出即生效，直到过期；适合邮件/短信退订链接场景
 *   <li>幂等：同一 (userId, topicCode, channel) 多次退订只会把状态置为 UNSUBSCRIBED， 不会重复插入记录
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
@RequiredArgsConstructor
public class UnsubscribeTokenUtil {
  /** Token 分段数 */
  private static final int TOKEN_PART_COUNT = 4;

  /** 过期时间分段索引 */
  private static final int EXPIRES_AT_INDEX = 3;


  /** payload 字段分隔符 */
  private static final String SEP = "|";

  /** 开发环境默认密钥（生产必须通过 ydsz.message.unsubscribe.secret 覆盖） */
  private static final String DEFAULT_SECRET =
      "ydsz-default-unsubscribe-secret-DO-NOT-USE-IN-PROD-CHANGE-IT";

  private final MessageProperties messageProperties;

  /**
   * 生成退订 token。
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 通道
   * @return 签名后的 token 字符串
   */
  public String generate(String userId, String topicCode, String channel) {
    if (!StringUtils.hasText(userId)
        || !StringUtils.hasText(topicCode)
        || !StringUtils.hasText(channel)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("用户 ID、主题编码与通道不能为空")
          .build();
    }
    int ttlDays = Math.max(1, messageProperties.getUnsubscribe().getTtlDays());
    long expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS).getEpochSecond();
    String payload = buildPayload(userId, topicCode, channel, expiresAt);
    String sig = sign(payload);
    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8))
        + "."
        + sig;
  }

  /**
   * 解析并校验 token。
   *
   * <p>校验项：
   *
   * <ol>
   *   <li>格式：必须为 {@code base64url(base64url)} 两段
   *   <li>签名：HMAC 必须与 payload 匹配
   *   <li>过期：expiresAt 必须大于当前时间
   * </ol>
   *
   * @param token token 字符串
   * @return 载荷
   * @throws SysException 校验失败时抛出 BAD_REQUEST
   */
  public UnsubscribeTokenPayload parseAndVerify(String token) {
    if (!StringUtils.hasText(token)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("退订 token 不能为空")
          .build();
    }
    String[] parts = token.split("\\.");
    if (parts.length != 2) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("退订 token 格式非法")
          .build();
    }
    String payloadB64 = parts[0];
    String sig = parts[1];
    String payload;
    try {
      payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("退订 token 解码失败")
          .build();
    }
    String expectedSig = sign(payload);
    if (!DigestUtils.constantTimeEquals(expectedSig, sig)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("退订 token 签名校验失败")
          .build();
    }
    UnsubscribeTokenPayload result = parsePayload(payload);
    if (Instant.now().getEpochSecond() > result.getExpiresAt()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("退订 token 已过期")
          .build();
    }
    return result;
  }

  /**
   * 拼接完整退订链接。
   *
   * <p>当 {@code ydsz.message.unsubscribe.base-url} 未配置时返回 token 本身。
   *
   * @param token token 字符串
   * @return 完整 URL 或 token
   */
  public String buildUrl(String token) {
    String base = messageProperties.getUnsubscribe().getBaseUrl();
    if (!StringUtils.hasText(base)) {
      return token;
    }
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return normalizedBase + "?token=" + token;
  }

  private String buildPayload(String userId, String topicCode, String channel, long expiresAt) {
    return userId + SEP + topicCode + SEP + channel + SEP + expiresAt;
  }

  private UnsubscribeTokenPayload parsePayload(String payload) {
    String[] parts = payload.split("\\" + SEP, -1);
    if (parts.length != TOKEN_PART_COUNT) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("退订 token 载荷格式非法")
          .build();
    }
    long expiresAt;
    try {
      expiresAt = Long.parseLong(parts[EXPIRES_AT_INDEX]);
    } catch (NumberFormatException e) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("退订 token 载荷格式非法")
          .build();
    }
    return new UnsubscribeTokenPayload(parts[0], parts[1], parts[2], expiresAt);
  }

  private String sign(String payload) {
    String configured = messageProperties.getUnsubscribe().getSecret();
    String secret = StringUtils.hasText(configured) ? configured : DEFAULT_SECRET;
    return DigestUtils.hmacSha256UrlSafe(payload, secret.getBytes(StandardCharsets.UTF_8));
  }
}
