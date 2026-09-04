package com.njydsz.message.domain.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息发送 DTO — 统一发送端点 {@code POST /send} 的请求体。
 *
 * <p>通过 {@link #strategy} 字段区分发送模式，替代原有的多个独立发送端点：
 *
 * <ul>
 *   <li><b>SYNC</b>：同步发送，阻塞返回供应商结果
 *   <li><b>DIRECT</b>：直接发送，使用本模块 DTO 扩展字段
 *   <li><b>ASYNC</b>：异步发送，先落库 PENDING 再投递 MQ
 *   <li><b>TRANSACTIONAL</b>：事务消息，RocketMQ 半消息机制
 *   <li><b>BATCH</b>：批量发送，同步循环限制 100 条/批（需配合 {@link #batchRequests} + {@link #batchId}）
 * </ul>
 *
 * <p>支持两种发送模式：
 *
 * <ul>
 *   <li><b>模板发送</b>：指定 {@code templateCode} + {@code params}，由模板引擎渲染最终内容
 *   <li><b>直接发送</b>：指定 {@code content} 直接发送原始内容（不走模板，适合动态内容场景）
 * </ul>
 *
 * <p>发送后由 {@code MessageService} 统一走渠道路由 → 限流 → 发送 → 回执 → 重试 全链路。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.domain.enums.core.SendStrategyEnum 发送策略枚举
 * @see com.njydsz.message.domain.enums.core.MessageChannelEnum 消息通道枚举
 */
@Data
public class MessageSendDTO {

  /** 发送策略（SYNC / DIRECT / ASYNC / TRANSACTIONAL / BATCH） */
  @NotNull(message = "发送策略不能为空")
  private SendStrategyEnum strategy = SendStrategyEnum.SYNC;

  /** 通道 */
  @Xss private String channel;

  /** 模板编码 */
  @Xss private String templateCode;

  /** 接收人 */
  @Xss private String receiver;

  /** 模板参数(用于占位符渲染) */
  private Map<String, Object> params;

  /** 直接发送的内容(不走模板) */
  private String content;

  /** 邮件主题(仅 EMAIL) */
  @Xss private String subject;

  /** 业务类型 */
  @Xss private String bizType;

  /** 业务单据 ID */
  @Xss private String bizId;

  /** 发送优先级 */
  @Xss private String priority;

  /** 消息唯一标识(用于幂等去重) */
  @Xss private String messageId;

  /** 触发发送的用户 ID */
  @Xss private String senderId;

  /** 聚合组 */
  @Xss private String messageGroup;

  /** 语言区域 */
  @Xss private String locale;

  /**
   * 批量请求列表（仅 strategy=BATCH 时使用）。
   *
   * <p>单次最多 100 条，超出返回 400。
   */
  private List<MessageRequest> batchRequests;

  /**
   * 批次 ID（仅 strategy=BATCH 时使用，业务侧生成，用于进度查询）。
   */
  @Xss private String batchId;
}
