package com.njydsz.message.server.service.receipt;

import java.util.List;

/**
 * P1-3: 全通道消息已读/未读状态同步服务。
 *
 * <p>统一管理消息已读状态的更新和实时同步：
 * <ul>
 *   <li>更新消息日志的 receipt_status 为 READ</li>
 *   <li>更新站内通知的 read_status 为 1</li>
 *   <li>通过 WebSocket 推送已读状态变更事件</li>
 *   <li>记录用户活跃行为（供 P1-1 智能推送时间优化使用）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ReadStatusSyncService {

    /**
     * 标记单条消息为已读（按 msgId）。
     *
     * @param msgId  消息 ID
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
     * @param userId         用户 ID
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
     * @param userId  用户 ID
     * @param channel 通道
     * @return 未读数量
     */
    long getUnreadCountByChannel(String userId, String channel);
}
