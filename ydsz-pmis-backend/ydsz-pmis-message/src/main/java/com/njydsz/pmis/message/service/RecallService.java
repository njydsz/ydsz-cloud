package com.njydsz.pmis.message.service;


/**
 * 消息撤回服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RecallService {

    /**
     * 撤回站内通知
     *
     * @param userId         用户 ID
     * @param notificationId 通知 ID
     * @return true 表示撤回成功
     */
    boolean recallNotification(String userId, String notificationId);

    /**
     * 撤回已发送消息(按日志 ID)
     *
     * @param logId 日志 ID
     * @return true 表示撤回成功
     */
    boolean recallMessage(String logId);

    /**
     * 按业务类型 + 单据 ID 批量撤回
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @return 撤回条数
     */
    int recallBatch(String bizType, String bizId);
}
