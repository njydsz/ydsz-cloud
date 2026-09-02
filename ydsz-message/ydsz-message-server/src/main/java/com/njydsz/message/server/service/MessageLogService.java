package com.njydsz.message.server.service.core;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 消息发送日志 Service
 *
 * <p>管理 {@code ydsz_msg_log}（消息发送日志）的查询、状态流转、重试、死信、重发。 是消息中心运维和可观测的"事实表"——所有发送最终都沉淀为日志,可按任意维度筛选分析。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>查询</b>：{@link #getById} / {@link #page} — 详情 + 分页
 *   <li><b>状态流转</b>：{@link #markRetry} / {@link #markDead} / {@link #updateReceipt} / {@link
 *       #markRecalled}
 *   <li><b>死信重发</b>：{@link #resendDead} — 手动将 DEAD 状态的日志重置并重新投递
 * </ul>
 *
 * <p><b>日志状态机：</b>{@code PENDING → SENDING → SUCCESS / FAILED → RETRY → SENDING → ... → DEAD}。
 *
 * <p><b>事务：</b>{@link #resendDead} 开启 {@code @Transactional(rollbackFor = Exception.class)},
 * 重置后立即通过 {@code ChannelRouter} 重新投递。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see MsgLogVO 消息日志 VO
 * @see MessageStatsService 消息统计服务
 */
public interface MessageLogService {

  /**
   * 根据 ID 查询日志
   *
   * @param id 日志 ID
   * @return 日志 VO
   */
  MsgLogVO getById(String id);

  /**
   * 分页查询日志
   *
   * @param query 查询参数
   * @return 分页结果
   */
  PageResponse<List<MsgLogVO>> page(MessageLogQueryDTO query);

  /**
   * 标记日志为重试中,并设置下次重试时间
   *
   * @param id 日志 ID
   * @param nextRetryAt 下次重试时间
   */
  void markRetry(String id, LocalDateTime nextRetryAt);

  /**
   * 标记日志为死信
   *
   * @param id 日志 ID
   * @param errorMessage 错误信息
   */
  void markDead(String id, String errorMessage);

  /**
   * 更新回执状态与回执时间
   *
   * @param id 日志 ID
   * @param receiptStatus 回执状态
   * @param receiptAt 回执时间
   */
  void updateReceipt(String id, String receiptStatus, LocalDateTime receiptAt);

  /**
   * 标记日志为已撤回
   *
   * @param id 日志 ID
   */
  void markRecalled(String id);

  /**
   * P1-4: 手动重发死信。
   *
   * <p>仅 DEAD 状态可重发。重置 retryCount / errorMessage / nextRetryAt， 流转为 SENDING 后立即通过 {@code
   * ChannelRouter} 重新投递：
   *
   * <ul>
   *   <li>投递成功 → SUCCESS
   *   <li>投递失败 → RETRY（进入正常重试调度,以全新 retryCount 计数）
   * </ul>
   *
   * @param logId 日志 ID
   */
  void resendDead(String logId);
}
