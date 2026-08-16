package com.njydsz.message.server.service.impl.receipt;

import java.time.Duration;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.receipt.ReadReceiptService;

/**
 * 全通道已读回执服务实现。
 *
 * <p>P2-12: 实现邮件追踪像素和短信短链的生成与回调处理。
 *
 * <p>邮件追踪像素：
 *
 * <ul>
 *   <li>生成：在 HTML {@code </body>} 前注入 {@code <img
 *       src="https://domain/api/read-receipt/pixel/{base64(msgId)}" />}
 *   <li>回调：GET 请求像素 URL → 标记消息已读 → 返回 1x1 透明 PNG
 * </ul>
 *
 * <p>短信短链：
 *
 * <ul>
 *   <li>生成：Redis 存储映射 {@code ydsz:shortlink:{code} → originalUrl}，TTL 7 天
 *   <li>回调：GET 请求短链 URL → 标记消息已读 → 302 重定向到原始 URL
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadReceiptServiceImpl implements ReadReceiptService {

  /** Redis 模板（短链映射 / 已读状态） */
  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final RedisStringOps redisStringOps;

  /** P3-3.2: tracking / shortlink base-url 统一从 MessageProperties 读取 */
  private final MessageProperties messageProperties;

  /** 短链映射 Redis key 前缀 */
  private static final String SHORTLINK_PREFIX = "ydsz:shortlink:";

  /** 已读状态 Redis key 前缀 */
  private static final String READ_STATUS_PREFIX = "ydsz:read:";

  /** 短链消息 ID 映射前缀 */
  private static final String SHORTLINK_MSG_PREFIX = "ydsz:shortlink:msg:";

  /** 短链 TTL（7 天） */
  private static final long SHORTLINK_TTL = 7 * 24 * 3600L;

  /**
   * 为 HTML 邮件正文注入不可见追踪像素，用于邮件已读回执。
   *
   * <p>在 {@code </body>} 前注入 1x1 透明跟踪图（URL 含 Base64 编码的 msgId）；无 {@code </body>} 时追加到末尾。 非 HTML
   * 内容或参数缺失时原样返回，不做注入。
   *
   * @param htmlContent 邮件 HTML 正文
   * @param msgId 消息 ID（用于回执关联）
   * @return 注入追踪像素后的 HTML；参数非法或内容非 HTML 时返回原内容
   */
  @Override
  public String injectEmailTrackingPixel(String htmlContent, String msgId) {
    if (!StringUtils.hasText(htmlContent) || !StringUtils.hasText(msgId)) {
      return htmlContent;
    }
    // 仅对 HTML 内容注入
    if (!htmlContent.trim().toLowerCase().contains("<html")
        && !htmlContent.trim().toLowerCase().contains("<body")) {
      // 非 HTML 格式，不注入
      return htmlContent;
    }
    String encodedMsgId = Base64.getUrlEncoder().withoutPadding().encodeToString(msgId.getBytes());
    String trackingBaseUrl = messageProperties.getTracking().getBaseUrl();
    String pixelUrl = trackingBaseUrl + "/api/read-receipt/pixel/" + encodedMsgId;
    String pixel =
        "<img src=\""
            + pixelUrl
            + "\" width=\"1\" height=\"1\" "
            + "style=\"display:none;border:0;outline:none;\" alt=\"\" />";
    // 在 </body> 前注入，无 </body> 则追加到末尾
    String lower = htmlContent.toLowerCase();
    int bodyCloseIdx = lower.lastIndexOf("</body>");
    if (bodyCloseIdx >= 0) {
      return htmlContent.substring(0, bodyCloseIdx) + pixel + htmlContent.substring(bodyCloseIdx);
    }
    return htmlContent + pixel;
  }

  /**
   * 为短信生成短链，用于短信已读回执与点击统计。
   *
   * <p>短码由雪花算法生成，Redis 中以 {@code ydsz:shortlink:{code} → originalUrl}、 {@code
   * ydsz:shortlink:msg:{code} → msgId} 存储，TTL 7 天。写入失败时降级返回原始 URL， 保证短信发送主流程不因短链异常中断。
   *
   * @param originalUrl 原始长链接（为空则原样返回）
   * @param msgId 消息 ID（可选，用于回执关联）
   * @return 短链 URL；生成失败或 originalUrl 为空时返回 originalUrl
   */
  @Override
  public String generateShortLink(String originalUrl, String msgId) {
    if (!StringUtils.hasText(originalUrl)) {
      return originalUrl;
    }
    String code = String.valueOf(snowflakeIdGenerator.nextId());
    String shortlinkBaseUrl = messageProperties.getShortlink().getBaseUrl();
    String shortUrl = shortlinkBaseUrl + "/s/" + code;
    try {
      redisStringOps.set(SHORTLINK_PREFIX + code, originalUrl, Duration.ofSeconds(SHORTLINK_TTL));
      if (StringUtils.hasText(msgId)) {
        redisStringOps.set(SHORTLINK_MSG_PREFIX + code, msgId, Duration.ofSeconds(SHORTLINK_TTL));
      }
      log.debug("[ReadReceipt] 短链生成: code={} url={} msgId={}", code, originalUrl, msgId);
    } catch (Exception e) {
      log.warn("[ReadReceipt] 短链生成失败,返回原始 URL: {}", e.getMessage(), e);
      return originalUrl;
    }
    return shortUrl;
  }

  /**
   * 处理邮件追踪像素回调，标记邮件已读。
   *
   * <p>在 Redis 写入 {@code ydsz:read:email:{msgId}=1}（TTL 30 天）；msgId 为空直接忽略， 写入异常仅告警不影响主流程。
   *
   * @param msgId 消息 ID
   */
  @Override
  public void handleEmailRead(String msgId) {
    if (!StringUtils.hasText(msgId)) {
      return;
    }
    try {
      redisStringOps.set(READ_STATUS_PREFIX + "email:" + msgId, "1", Duration.ofDays(30));
      log.info("[ReadReceipt] 邮件已读: msgId={}", msgId);
    } catch (Exception e) {
      log.warn("[ReadReceipt] 邮件已读标记失败: msgId={} err={}", msgId, e.getMessage(), e);
    }
  }

  /**
   * 处理短信短链点击回调，返回原始 URL 并标记已读。
   *
   * <p>根据短码查 Redis：命中则重定向到 originalUrl，并对关联 msgId 标记短信已读（TTL 30 天）； 短码不存在/已过期或查询异常时返回
   * null，由调用方决定降级行为。
   *
   * @param shortCode 短链编码
   * @return 原始长链接；短码无效或异常时返回 null
   */
  @Override
  public String handleShortLinkClick(String shortCode) {
    if (!StringUtils.hasText(shortCode)) {
      return null;
    }
    try {
      String originalUrl = redisStringOps.get(SHORTLINK_PREFIX + shortCode, String.class);
      if (originalUrl == null) {
        log.warn("[ReadReceipt] 短链不存在或已过期: code={}", shortCode);
        return null;
      }
      // 标记消息已读
      String msgId = redisStringOps.get(SHORTLINK_MSG_PREFIX + shortCode, String.class);
      if (StringUtils.hasText(msgId)) {
        redisStringOps.set(READ_STATUS_PREFIX + "sms:" + msgId, "1", Duration.ofDays(30));
        log.info("[ReadReceipt] 短信已读: msgId={} code={}", msgId, shortCode);
      }
      return originalUrl;
    } catch (Exception e) {
      log.warn("[ReadReceipt] 短链点击处理失败: code={} err={}", shortCode, e.getMessage(), e);
      return null;
    }
  }

  /**
   * 判断消息是否已读（邮件或短信任一渠道已读即视为已读）。
   *
   * <p>查询 Redis 中 {@code email:} 与 {@code sms:} 两个已读标记；查询异常时保守返回 false， 避免将未知状态误判为已读。
   *
   * @param msgId 消息 ID
   * @return true 表示邮件或短信渠道已标记已读
   */
  @Override
  public boolean isRead(String msgId) {
    if (!StringUtils.hasText(msgId)) {
      return false;
    }
    try {
      Boolean emailRead = redisStringOps.hasKey(READ_STATUS_PREFIX + "email:" + msgId);
      Boolean smsRead = redisStringOps.hasKey(READ_STATUS_PREFIX + "sms:" + msgId);
      return Boolean.TRUE.equals(emailRead) || Boolean.TRUE.equals(smsRead);
    } catch (Exception e) {
      return false;
    }
  }
}
