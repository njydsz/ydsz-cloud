package com.njydsz.message.server.service.receipt;

import java.util.List;

/**
 * 已读状态同步服务接口。
 *
 * <p>IM 渠道已读回执同步至消息中心。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ReadStatusSyncService {

  /**
   * 标记单条消息为已读（按 msgId）。
   *
   * @param msgId 消息 ID
   * @param userId 用户 ID
   * @return true 表示状态更新成功
   */
  boolean markRead(String msgId, String userId);

  /**
   * 批量标记消息为已读。
   *
   * @param msgIds 消息 ID 列表
   * @param userId 用户 ID
   * @return 成功标记的条数
   */
  int markReadBatch(List<String> msgIds, String userId);

  /**
   * 标记站内通知为已读（按通知 ID）。
   *
   * @param notificationId 通知 ID
   * @param userId 用户 ID
   * @return true 表示状态更新成功
   */
  boolean markNotificationRead(String notificationId, String userId);

  /**
   * 批量标记站内通知为已读（按用户 ID 全部标记或按 bizType 批量标记）。
   *
   * @param userId 用户 ID
   * @param bizType 业务类型（为 null 时标记该用户全部未读通知）
   * @return 成功标记的条数
   */
  int markAllNotificationsRead(String userId, String bizType);

  /**
   * 查询用户未读消息数量。
   *
   * @param userId 用户 ID
   * @return 未读数量
   */
  long getUnreadCount(String userId);

  /**
   * 查询用户未读消息数量（按通道）。
   *
   * @param userId 用户 ID
   * @param channel 通道
   * @return 未读数量
   */
  long getUnreadCountByChannel(String userId, String channel);
}
