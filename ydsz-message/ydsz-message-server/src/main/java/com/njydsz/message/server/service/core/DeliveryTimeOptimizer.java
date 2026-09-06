package com.njydsz.message.server.service.core;

import java.time.LocalDateTime;

/**
 * 送达时间优化器。
 *
 * <p>基于用户时区推荐最佳发送时机，仅对 LOW 优先级的营销消息生效。 核心消息（验证码、告警等）要求即时送达，不应延迟。
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
 * @since 26.09.01
 */
public interface DeliveryTimeOptimizer {

  /**
   * 获取用户最佳推送时间。
   *
   * <p>基于用户时区和配置的推荐时段，返回未来最近的推荐发送时间。 如果用户时区未知或无推荐时段，返回 null（由调用方使用当前时间）。
   *
   * @param userId 用户 ID
   * @param channel 通道
   * @return 推荐的推送时间；null 表示无足够数据，使用当前时间
   */
  LocalDateTime getOptimalDeliveryTime(String userId, String channel);
}
