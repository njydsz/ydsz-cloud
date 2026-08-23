package com.njydsz.message.server.service.impl.receipt;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.message.server.service.receipt.ReadReceiptService;

/**
 * 已读回执服务实现。
 *
 * <p>基于 Redis 存储已读状态，支持邮件和短信通道的已读回执。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadReceiptServiceImpl implements ReadReceiptService {
  /** 已读状态保留天数 */
  private static final int READ_STATUS_TTL_DAYS = 30;


  private final RedisStringOps redisStringOps;

  /** 已读状态 Redis key 前缀 */
  private static final String READ_STATUS_PREFIX = "ydsz:read:";

  /** 短链消息 ID 映射前缀 */
  private static final String SHORTLINK_MSG_PREFIX = "ydsz:shortlink:msg:";

  /** 短链映射 Redis key 前缀 */
  private static final String SHORTLINK_PREFIX = "ydsz:shortlink:";

  /**
   * 处理邮件已读回调，标记邮件已读。   *
   * <p>在 Redis 写入 {@code ydsz:read:email:{msgId}=1}（TTL 30 天）；msgId 为空直接忽略，
   * 写入异常仅告警不影响主流程。
   *
   * @param msgId 消息 ID
   */
  @Override
  public void handleEmailRead(String msgId) {
    if (!StringUtils.hasText(msgId)) {
      return;
    }
    try {
      redisStringOps.set(READ_STATUS_PREFIX + "email:" + msgId, "1", Duration.ofDays(READ_STATUS_TTL_DAYS));
      log.info("[ReadReceipt] 邮件已读: msgId={}", msgId);
    } catch (Exception e) {
      log.warn("[ReadReceipt] 邮件已读标记失败: msgId={} err={}", msgId, e.getMessage(), e);
    }
  }

  /**
   * 处理短链点击回调，返回原始 URL 并标记已读。   *
   * <p>根据短码查 Redis：命中则重定向到 originalUrl，并对关联 msgId 标记短信已读（TTL 30 天）；
   * 短码不存在/已过期或查询异常时返回 null，由调用方决定降级行为。
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
        redisStringOps.set(READ_STATUS_PREFIX + "sms:" + msgId, "1", Duration.ofDays(READ_STATUS_TTL_DAYS));
        log.info("[ReadReceipt] 短信已读: msgId={} code={}", msgId, shortCode);
      }
      return originalUrl;
    } catch (Exception e) {
      log.warn("[ReadReceipt] 短链点击处理失败: code={} err={}", shortCode, e.getMessage(), e);
      return null;
    }
  }

  /**
   * 判断消息是否已读（邮件或短信任一渠道已读即视为已读）。   *
   * <p>查询 Redis 中 {@code email:} 与 {@code sms:} 两个已读标记；查询异常时保守返回 false，
   * 避免将未知状态误判为已读。
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
