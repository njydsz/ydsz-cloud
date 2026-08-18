package com.njydsz.message.server.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.core.DeliveryTimeOptimizer;

/**
 * 送达时间优化器实现。
 *
 * <p>基于用户时区的固定时段推荐最佳发送时机，仅对 LOW 优先级的营销消息生效。 删除 Redis 画像逻辑，简化为基于时区的时段判断，降低 Redis 存储成本和运维复杂度。
 *
 * <p>推荐策略：
 *
 * <ul>
 *   <li>在推荐发送时段内（默认 10:00-20:00）返回当前时间
 *   <li>在时段之前返回当天时段开始时间
 *   <li>在时段之后返回次日时段开始时间
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class DeliveryTimeOptimizerImpl implements DeliveryTimeOptimizer {

  /** 推荐发送时段开始小时（默认 10:00） */
  private static final int RECOMMENDED_START_HOUR = 10;

  /** 推荐发送时段结束小时（默认 20:00） */
  private static final int RECOMMENDED_END_HOUR = 20;

  private final MessageProperties messageProperties;

  public DeliveryTimeOptimizerImpl(MessageProperties messageProperties) {
    this.messageProperties = messageProperties;
  }

  @Override
  public LocalDateTime getOptimalDeliveryTime(String userId, String channel) {
    if (!StringUtils.hasText(userId)) {
      return null;
    }
    try {
      // 获取用户时区，未知时区使用系统默认时区
      ZoneId userZone = resolveUserZone(userId);
      LocalDateTime now = LocalDateTime.now(userZone);
      int currentHour = now.getHour();

      // 在推荐时段内，返回当前时间
      if (currentHour >= RECOMMENDED_START_HOUR && currentHour < RECOMMENDED_END_HOUR) {
        log.debug("[DeliveryTime] 当前在推荐时段内,立即发送: userId={} hour={}", userId, currentHour);
        return now;
      }

      // 在时段之前，返回今天时段开始时间
      if (currentHour < RECOMMENDED_START_HOUR) {
        LocalDateTime scheduledAt = now.toLocalDate().atTime(RECOMMENDED_START_HOUR, 0);
        log.debug("[DeliveryTime] 推荐今天时段开始: userId={} scheduledAt={}", userId, scheduledAt);
        return scheduledAt;
      }

      // 在时段之后，返回明天时段开始时间
      LocalDateTime scheduledAt = now.toLocalDate().plusDays(1).atTime(RECOMMENDED_START_HOUR, 0);
      log.debug("[DeliveryTime] 推荐明天时段开始: userId={} scheduledAt={}", userId, scheduledAt);
      return scheduledAt;
    } catch (Exception e) {
      log.warn("[DeliveryTime] 获取最佳推送时间失败,降级立即发送: userId={} err={}", userId, e.getMessage());
      return null;
    }
  }

  /**
   * 解析用户时区。
   *
   * <p>实际项目中可从用户偏好或租户配置获取，当前简化为系统默认时区。
   *
   * @param userId 用户 ID
   * @return 用户时区
   */
  private ZoneId resolveUserZone(String userId) {
    // TODO: 后续可从 UserChannelBindingService 或租户配置获取用户时区
    return TimeZone.getDefault().toZoneId();
  }
}
