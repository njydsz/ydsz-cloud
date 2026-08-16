package com.njydsz.message.server.service.core;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.dto.batch.BatchSendResult;
import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.core.MessageSendDTO;
import com.njydsz.message.domain.entity.core.MsgLog;

/**
 * 消息发送 Service 接口（多渠道核心入口）
 *
 * <p>提供全渠道（站内/短信/邮件/企业 IM/推送）消息的发送、日志查询、事务消息等核心能力。 是消息中心（ydsz-message）的<b>统一入口</b>，所有消息发送都通过本
 * Service 进入。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>同步发送</b>：{@link #send}（跨模块标准 DTO）/ {@link #sendDirect}（本模块 DTO）
 *   <li><b>批量发送</b>：{@link #batchSend} — 同步循环，单批 ≤ 100 条
 *   <li><b>事务消息</b>：{@link #sendTransactionally} — RocketMQ 半消息 + 本地事务校验
 *   <li><b>异步发送</b>：{@link #sendAsync} — 落库 PENDING → MQ → 消费者回调
 *   <li><b>日志查询</b>：{@link #pageLog} — 发送日志分页查询
 * </ul>
 *
 * <p><b>通道路由：</b>由 {@code RouteRuleService} 按"用户偏好 → 路由规则 → 默认通道"三级决策 选择最终通道，未匹配规则时降级到站内通知。
 *
 * <p><b>可靠性保证：</b>
 *
 * <ul>
 *   <li>所有发送请求先落库（{@code MsgLog}）再投递，DB 是 Source of Truth
 *   <li>失败时由 {@code RetryScanner} 自动重试（指数退避 + 最大次数）
 *   <li>超过 {@code ydsz.message.receipt-timeout-minutes} 未回执标记为 TIMEOUT
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.RouteRuleService 路由规则 Service
 * @see com.njydsz.message.server.service.ReceiptService 回执 Service
 * @see com.njydsz.message.domain.entity.core.MsgLog 消息日志实体
 */
public interface MessageService {

  /**
   * 基于跨模块共享请求发送消息（标准 Feign 入口）
   *
   * <p>通过 {@code MessageRequest} 跨服务传递参数，是各业务模块通过 {@code MessageClient}（Feign）调用的标准入口。
   *
   * @param request 消息发送请求（channel / receiver / templateCode / variables / businessType）
   * @return 发送结果（success / messageId / errorCode / errorMessage）
   */
  MessageResult send(MessageRequest request);

  /**
   * 直接发送消息（走本模块 DTO）
   *
   * <p>供 ydsz-message 内部 Controller 使用，比 {@link #send} 多了本模块扩展字段 （如 {@code scheduledAt} 定时发送、{@code
   * priority} 优先级）。
   *
   * @param dto 发送参数
   * @return 发送结果
   */
  MessageResult sendDirect(MessageSendDTO dto);

  /**
   * 批量发送消息（同步循环，限制 100 条/批）。
   *
   * <p>每条请求的 bizId 会统一设置为 batchId，便于后续进度查询。 限制：单批最多 100 条，超出抛异常；失败条目不影响其他条目。
   *
   * @param requests 消息请求列表
   * @param batchId 批次 ID（业务侧生成）
   * @return 批量发送结果（含成功/失败/跳过计数 + 失败明细）
   */
  BatchSendResult batchSend(List<MessageRequest> requests, String batchId);

  /**
   * 分页查询消息发送日志
   *
   * <p>支持按 {@code channel / receiver / templateCode / status / businessType} 多条件过滤。 走 {@code
   * idx_created_at} 索引，按创建时间倒序。
   *
   * @param query 查询参数（pageNum / pageSize / 多条件）
   * @return 分页结果
   */
  Page<MsgLog> pageLog(MessageLogQueryDTO query);

  /**
   * P2-3: 事务消息发送（RocketMQ 半消息）。
   *
   * <p>发送半消息后，由 {@link com.njydsz.message.server.producer.MessageTransactionListener}
   * 执行本地事务校验（通道/模板有效性），COMMIT 后消费端异步处理。 适用于业务侧需要确保通知请求仅在本地校验通过后才投递的场景。
   *
   * @param request 消息发送请求
   * @return 发送结果（success=true 表示半消息已提交，实际发送由消费端异步完成）
   */
  MessageResult sendTransactionally(MessageRequest request);

  /**
   * P0-3: 异步发送消息（先落库 PENDING → 再投递 MQ）。
   *
   * <p>可靠性保证：先将消息请求落库为 PENDING 状态（DB 是 Source of Truth）， 然后投递到 MQ。消费端处理后更新状态为 SUCCESS/FAILED/RETRY。
   * 若 MQ 投递失败，PENDING 记录可被恢复扫描器拾取补偿。
   *
   * @param request 消息发送请求
   * @return 发送结果（含 messageId 供追踪，success=true 表示已落库+已投递 MQ）
   */
  MessageResult sendAsync(MessageRequest request);

  /**
   * P1-F3: 取消定时消息（仅允许取消状态为 SCHEDULED 的消息）。
   *
   * <p>定时消息到达 scheduledAt 后由定时扫描器触发发送；若在触发前需要取消， 通过此接口将状态从 SCHEDULED 流转到 SKIPPED，后续扫描器会跳过该消息。
   *
   * <p>已发送（SUCCESS/FAILED）或已撤回（RECALLED）的消息不可取消。
   *
   * @param msgId 消息 ID（定时消息落库时返回的 msgId）
   * @return 取消结果（success=true 表示已取消）
   */
  MessageResult cancelScheduledMessage(String msgId);
}
