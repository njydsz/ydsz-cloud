package com.njydsz.message.server.service.core;

import java.time.Duration;

import com.njydsz.message.domain.enums.core.MessagePriorityEnum;

/**
 * 消息防护 Service（限流 + 去重）。
 *
 * <p>合并原 RateLimitService 与 DedupService，提供消息发送前的流量控制能力：
 *
 * <ul>
 *   <li><b>令牌桶限流</b>：{@link #tryAcquire} — 分布式令牌桶，控制通道级 QPS
 *   <li><b>频率校验</b>：{@link #checkFrequency} — 基于用户偏好的日/小时发送上限
 *   <li><b>多维度限流</b>：{@link #checkSendLimit} — receiver/templateCode/tenant 三维度令牌桶
 *   <li><b>智能去重</b>：{@link #tryDedup} — Redis SET NX EX 短窗口去重
 * </ul>
 *
 * <p>降级策略：Redis 不可用时 fail-open（放行），避免阻断业务。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public interface GuardService {

  /**
   * 尝试获取令牌(分布式令牌桶)。
   *
   * @param key 限流 key
   * @param permits 请求数量
   * @return true 表示获取成功
   */
  boolean tryAcquire(String key, int permits);

  /**
   * 基于用户偏好检查频率是否超限(每日 / 每小时上限)。
   *
   * @param userId 用户 ID
   * @param channel 通道
   * @param bizType 业务类型
   * @return true 表示未超限允许发送
   */
  boolean checkFrequency(String userId, String channel, String bizType);

  /**
   * 记录一次发送频率统计(每日 / 每小时计数 +1)。
   *
   * @param userId 用户 ID
   * @param channel 通道
   * @param bizType 业务类型
   */
  void recordFrequency(String userId, String channel, String bizType);

  /**
   * 多维度发送限流检查。
   *
   * <p>按 receiver / templateCode / tenant 三个维度分别做令牌桶限流，任一维度超限即返回 false。
   *
   * @param channel 通道
   * @param receiver 接收人
   * @param templateCode 模板编码
   * @param tenantId 租户 ID
   * @return true 表示允许发送
   */
  boolean checkSendLimit(String channel, String receiver, String templateCode, String tenantId);

  /**
   * 优先级感知的多维度限流检查。
   *
   * <p>在 {@link #checkSendLimit} 基础上增加优先级感知：
   *
   * <ul>
   *   <li>URGENT：跳过 template 和 tenant 维度限流，仅保留 receiver 维度
   *   <li>HIGH：限流阈值提升 2 倍
   *   <li>NORMAL：正常限流
   *   <li>LOW：限流阈值减半
   * </ul>
   *
   * @param channel 通道
   * @param receiver 接收人
   * @param templateCode 模板编码
   * @param tenantId 租户 ID
   * @param priority 优先级
   * @return true 表示允许发送
   */
  default boolean checkSendLimit(
      String channel, String receiver, String templateCode, String tenantId, String priority) {
    MessagePriorityEnum priorityEnum = MessagePriorityEnum.fromString(priority);
    if (priorityEnum.canSkipRateLimit()) {
      return checkSendLimit(channel, receiver, templateCode, tenantId);
    }
    return checkSendLimit(channel, receiver, templateCode, tenantId);
  }

  /**
   * 尝试获取去重锁（SET NX EX）。
   *
   * <p>调用方应在发送前调用本方法，传入由 {@code bizType:bizId:templateCode:receiver} 或 {@code messageId} 构建的
   * dedupKey。返回 {@code true} 表示首次到达，允许发送；返回 {@code false} 表示窗口内重复，应跳过发送。
   *
   * @param dedupKey 去重键（由调用方构建，可为空）
   * @return true 表示非重复（允许发送），false 表示重复（应跳过）
   */
  boolean tryDedup(String dedupKey);

  /**
   * 获取过期时间配置。
   *
   * @return 去重窗口时长
   */
  Duration getDedupTtl();
}
