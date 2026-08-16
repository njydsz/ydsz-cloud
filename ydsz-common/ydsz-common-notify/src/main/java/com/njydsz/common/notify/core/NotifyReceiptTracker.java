package com.njydsz.common.notify.core;

import java.util.Optional;

/**
 * 通知回执追踪器
 *
 * <p>管理通知回执的生命周期：创建 → 投递 → 失败/已读。 实现类可使用 Redis、数据库或内存存储回执数据。
 *
 * <p>此为骨架接口，提供 {@link InMemoryNotifyReceiptTracker} 默认实现。 生产环境可替换为基于 Redis 或数据库的实现，支持持久化和跨实例查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface NotifyReceiptTracker {

  /**
   * 创建回执记录（发送前调用）
   *
   * @param messageId 消息唯一标识
   * @param channel 通知渠道名称
   * @param receiver 接收者标识
   * @return 创建的回执
   */
  NotifyReceipt createReceipt(String messageId, String channel, String receiver);

  /**
   * 标记回执为已投递
   *
   * @param messageId 消息唯一标识
   */
  void markDelivered(String messageId);

  /**
   * 标记回执为投递失败
   *
   * @param messageId 消息唯一标识
   * @param errorMessage 错误信息
   */
  void markFailed(String messageId, String errorMessage);

  /**
   * 标记回执为已读（接收者主动回执）
   *
   * @param messageId 消息唯一标识
   */
  void markRead(String messageId);

  /**
   * 查询回执
   *
   * @param messageId 消息唯一标识
   * @return 回执（若不存在返回 {@link Optional#empty()}）
   */
  Optional<NotifyReceipt> findReceipt(String messageId);
}
