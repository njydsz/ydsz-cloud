package com.njydsz.message.server.service.receipt;

/**
 * 已读回执 Service。
 *
 * <p>为邮件和短信通道提供"已读"回执能力。常规通道(IN_APP/PUSH/IM)的消息已读状态
 * 由前端主动调用 ReadStatusSyncService.markRead 触发，而邮件和短信因用户离线，
 * 需通过回调方式主动探测"用户是否打开/点击"。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>回执回调处理</b>：{@link #handleEmailRead} / {@link #handleShortLinkClick}
 *   <li><b>状态查询</b>：{@link #isRead}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ReadReceiptService {

  /**
   * 处理邮件已读回调。
   *
   * @param msgId 消息 ID
   */
  void handleEmailRead(String msgId);

  /**
   * 处理短链点击（短信已读回调），返回目标 URL 用于重定向。
   *
   * @param shortCode 短链 code
   * @return 目标 URL
   */
  String handleShortLinkClick(String shortCode);

  /**
   * 查询消息已读状态。
   *
   * @param msgId 消息 ID
   * @return true 表示已读
   */
  boolean isRead(String msgId);
}
