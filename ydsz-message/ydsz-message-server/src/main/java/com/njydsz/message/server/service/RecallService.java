package com.njydsz.message.server.service.receipt;

/**
 * 消息撤回服务接口。
 *
 * <p>撤回已发送的消息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface RecallService {

  /** P0-4: 消息撤回时间窗口（分钟），超过此时间不可撤回 */
  long RECALL_WINDOW_MINUTES = 30L;

  /**
   * 撤回站内通知
   *
   * @param userId 用户 ID
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
   * P0-4: 按 msgId 撤回已发送消息。
   *
   * <p>支持撤回时间窗口校验（默认 30 分钟内可撤回）， 撤回后通过 WebSocket 推送撤回事件到前端。
   *
   * @param msgId 消息 ID（ydsz_msg_log.msg_id）
   * @return true 表示撤回成功
   */
  boolean recallByMsgId(String msgId);

  /**
   * 按业务类型 + 单据 ID 批量撤回
   *
   * @param bizType 业务类型
   * @param bizId 业务单据 ID
   * @return 撤回条数
   */
  int recallBatch(String bizType, String bizId);
}
