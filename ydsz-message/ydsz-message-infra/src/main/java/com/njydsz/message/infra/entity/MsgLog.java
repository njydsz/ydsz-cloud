package com.njydsz.message.infra.entity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;

/**
 * 消息发送日志领域实体 — 全通道发送全量记录的事实表。
 *
 * <p>对应数据库表 {@code ydsz_msg_log}，是消息中心的核心事实表。
 *
 * <p>与 {@code MsgLog} 的区别：
 * <ul>
 *   <li>去除 MyBatis-Plus 持久化注解（{@code @TableName} 等）
 *   <li>状态/优先级/通道字段使用枚举类型替代 String
 *   <li>不继承 {@code MpBaseEntity}，审计字段平铺定义
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SuppressWarnings("unchecked") // @SuperBuilder 生成的代码会触发 unchecked 警告，无法在源码层面修复
@Data
@SuperBuilder
@NoArgsConstructor
public class MsgLog implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ===== 审计字段 =====
  private String id;
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
  private Boolean deleted;

  // ===== 业务字段 =====
  private MessageChannelEnum channel;
  private String bizType;
  private String bizId;
  private String receiver;
  private String templateCode;
  private String templateParams;
  private String content;
  private MessageStatusEnum status;
  private String errorMessage;
  private MessagePriorityEnum priority;
  private String senderId;
  private String messageGroup;
  private String batchId;
  private String routeRuleId;
  private Integer canary;
  private String canaryKey;
  private String dedupKey;
  private RecallStatusEnum recallStatus;
  private LocalDateTime recallAt;
  private ReceiptStatusEnum receiptStatus;
  private LocalDateTime receiptAt;
  private Integer retryCount;
  private LocalDateTime nextRetryAt;
  private String providerTraceId;
  private Long costMs;
  private BigDecimal cost;
  private String traceId;
  private String msgId;
  private String topic;
  private Integer reconsumeTimes;
  private String parentMsgId;
  private LocalDateTime scheduledAt;

  // ===== 领域行为 =====

  /**
   * 标记消息为发送中状态。
   *
   * <p>状态流转：待发送/重试 → 发送中
   *
   * @throws IllegalStateException 当当前状态不允许流转到发送中时
   */
  public void markAsSending() {
    validateTransition(MessageStatusEnum.SENDING);
    this.status = MessageStatusEnum.SENDING;
  }

  /**
   * 标记消息为发送成功。
   *
   * <p>状态流转：发送中 → 成功
   *
   * @param providerTraceId 服务商追踪 ID
   * @param costMs 发送耗时（毫秒）
   * @param cost 发送费用
   * @throws IllegalStateException 当当前状态不允许流转到成功时
   */
  public void markAsSuccess(String providerTraceId, long costMs, BigDecimal cost) {
    validateTransition(MessageStatusEnum.SUCCESS);
    this.status = MessageStatusEnum.SUCCESS;
    this.providerTraceId = providerTraceId;
    this.costMs = costMs;
    this.cost = cost;
  }

  /**
   * 标记消息为发送失败。
   *
   * <p>状态流转：发送中 → 失败
   *
   * @param errorMessage 错误信息
   * @throws IllegalStateException 当当前状态不允许流转到失败时
   */
  public void markAsFailed(String errorMessage) {
    validateTransition(MessageStatusEnum.FAILED);
    this.status = MessageStatusEnum.FAILED;
    this.errorMessage = errorMessage;
  }

  /**
   * 标记消息为重试状态。
   *
   * <p>状态流转：失败 → 重试，自动累加重试次数。
   *
   * @param nextRetryAt 下次重试时间
   * @throws IllegalStateException 当当前状态不允许流转到重试时
   */
  public void markAsRetry(LocalDateTime nextRetryAt) {
    validateTransition(MessageStatusEnum.RETRY);
    this.status = MessageStatusEnum.RETRY;
    this.nextRetryAt = nextRetryAt;
    if (this.retryCount == null) {
      this.retryCount = 1;
    } else {
      this.retryCount++;
    }
  }

  /**
   * 标记消息为已撤回。
   *
   * <p>状态流转：成功 → 已撤回，同时更新撤回状态和撤回时间。
   *
   * @throws IllegalStateException 当当前状态不允许流转到已撤回时
   */
  public void markAsRecalled() {
    validateTransition(MessageStatusEnum.RECALLED);
    this.status = MessageStatusEnum.RECALLED;
    this.recallStatus = RecallStatusEnum.RECALLED;
    this.recallAt = LocalDateTime.now();
  }

  /**
   * 标记消息为已跳过。
   *
   * <p>状态流转：待发送 → 已跳过（如通道熔断、去重命中时跳过发送）。
   *
   * @throws IllegalStateException 当当前状态不允许流转到已跳过时
   */
  public void markAsSkipped() {
    validateTransition(MessageStatusEnum.SKIPPED);
    this.status = MessageStatusEnum.SKIPPED;
  }

  /**
   * 判断消息是否可流转到目标状态。
   *
   * @param targetStatus 目标状态
   * @return true 表示允许流转
   */
  public boolean canTransitionTo(MessageStatusEnum targetStatus) {
    if (this.status == null) {
      return true;
    }
    return this.status.canTransitTo(targetStatus);
  }

  /**
   * 判断消息是否处于终态（成功/失败/已撤回/已跳过）。
   *
   * <p>终态消息不再参与任何状态流转。
   *
   * @return true 表示已处于终态
   */
  public boolean isTerminal() {
    if (this.status == null) {
      return false;
    }
    return this.status.isTerminal();
  }

  private void validateTransition(MessageStatusEnum targetStatus) {
    if (this.status != null && !this.status.canTransitTo(targetStatus)) {
      throw new IllegalStateException(
          String.format("状态流转非法: %s → %s", this.status, targetStatus));
    }
  }
}
