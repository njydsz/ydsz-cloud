package com.njydsz.message.server.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.message.server.config.MessageProperties;

/**
 * P2-13: 跨渠道抑制引擎。
 *
 * <p>防止同一业务事件在短时间内通过多个渠道重复触达用户（如同时收到短信+邮件+站内信）。
 *
 * <p>抑制策略：
 *
 * <ul>
 *   <li>同一 {@code bizType + bizId + receiver} 组合在抑制窗口内只允许首个渠道发送
 *   <li>后续渠道的发送请求被抑制（记录日志，返回 SKIP 结果）
 *   <li>抑制窗口可配置，默认 5 分钟
 * </ul>
 *
 * <p>Redis Key 格式：{@code suppress:{bizType}:{bizId}:{receiver}} → channel
 *
 * <p>TTL：抑制窗口时间
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSuppressionEngine {

  private final RedisStringOps redisStringOps;

  /** OD-7 / P3-3.2: 抑制窗口配置统一从 MessageProperties 读取 */
  private final MessageProperties messageProperties;

  /** OD-7: 抑制 Key 前缀 */
  private static final String SUPPRESS_KEY_PREFIX = "suppress:";

  /**
   * 检查是否应该抑制此消息。
   *
   * <p>如果同一 {@code bizType + bizId + receiver} 在抑制窗口内已有其他渠道发送， 则返回 true（抑制），否则返回 false（放行）并记录当前渠道。
   *
   * @param bizType 业务类型
   * @param bizId 业务单据 ID
   * @param receiver 接收者
   * @param channel 当前渠道
   * @return true 表示应该抑制，false 表示放行
   */
  public boolean shouldSuppress(String bizType, String bizId, String receiver, String channel) {
    if (bizType == null || bizId == null || receiver == null || channel == null) {
      return false;
    }
    long suppressWindowSeconds = messageProperties.getSuppressWindowSeconds();
    String key = buildKey(bizType, bizId, receiver);
    boolean acquired = redisStringOps.setIfAbsent(key, channel, suppressWindowSeconds);
    if (acquired) {
      // 首个渠道，放行
      log.debug(
          "[Suppress] 首渠道放行: bizType={} bizId={} receiver={} channel={}",
          bizType,
          bizId,
          receiver,
          channel);
      return false;
    }
    // 已有其他渠道发送，抑制
    String existingChannel = redisStringOps.get(key, String.class);
    log.info(
        "[Suppress] 跨渠道抑制: bizType={} bizId={} receiver={} current={} existing={}",
        bizType,
        bizId,
        receiver,
        channel,
        existingChannel);
    return true;
  }

  /**
   * 释放抑制锁（用于撤回等场景）。
   *
   * @param bizType 业务类型
   * @param bizId 业务单据 ID
   * @param receiver 接收者
   */
  public void release(String bizType, String bizId, String receiver) {
    String key = buildKey(bizType, bizId, receiver);
    redisStringOps.del(key);
  }

  private String buildKey(String bizType, String bizId, String receiver) {
    return SUPPRESS_KEY_PREFIX + bizType + ":" + bizId + ":" + receiver;
  }
}
