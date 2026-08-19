package com.njydsz.message.server.service.receipt;

import java.util.List;

import com.njydsz.message.domain.dto.ReceiptCallbackDTO;
import com.njydsz.message.domain.entity.receipt.MsgReceipt;

/**
 * 消息回执 Service
 *
 * <p>处理第三方通道服务商(SMS 网关、邮件 SMTP、推送通道、IM 机器人等)回传的"送达/失败/已读/已点击" 状态变更回调,更新 {@code
 * ydsz_msg_log.receipt_status} / {@code receipt_at} 字段, 并通过 WebSocket 推送回执事件到前端。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>回执回调</b>：{@link #callback} — 接收服务商 HTTP POST 回调,解析后落库
 *   <li><b>回执查询</b>：{@link #listByLogId} — 按消息日志 ID 查询回执历史
 * </ul>
 *
 * <p><b>回执状态机：</b>{@code NONE → DELIVERED → READ → CLICKED / FAILED / TIMEOUT}。
 *
 * <ul>
 *   <li>{@code DELIVERED} — 服务商确认已送达用户终端
 *   <li>{@code READ} — 用户打开消息(邮件/IM)
 *   <li>{@code CLICKED} — 用户点击消息中的链接
 *   <li>{@code FAILED} — 服务商报告发送失败
 *   <li>{@code TIMEOUT} — P2-9 回执闭环,超过 {@code ydsz.message.receipt-timeout-minutes} 仍无回执由 {@code
 *       ReceiptPuller} 标记
 * </ul>
 *
 * <p><b>幂等性：</b>同一条消息的多次回执以 {@code (msgId, receiptStatus, receiptAt)} 唯一键去重, 重复回调不会产生重复记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.entity.receipt.MsgReceipt 回执实体
 * @see ReadStatusSyncService 已读状态同步服务
 * @see ReadReceiptService 全通道已读回执服务(邮件追踪像素/短信短链)
 */
public interface ReceiptService {

  /**
   * 处理服务商回执回调
   *
   * @param dto 回执回调参数
   */
  void callback(ReceiptCallbackDTO dto);

  /**
   * 根据日志 ID 查询回执列表
   *
   * @param logId 日志 ID
   * @return 回执列表
   */
  List<MsgReceipt> listByLogId(String logId);
}
