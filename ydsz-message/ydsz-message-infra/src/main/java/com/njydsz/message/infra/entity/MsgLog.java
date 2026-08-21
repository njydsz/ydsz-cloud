package com.njydsz.message.infra.entity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 消息发送日志领域实体 — 全通道发送全量记录的事实表。
 *
 * <p>对应数据库表 {@code ydsz_msg_log}，是消息中心的核心事实表。
 *
 * <p>与 {@code MsgLogDO} 的区别：
 * <ul>
 *   <li>去除 MyBatis-Plus 持久化注解（{@code @TableName} 等）
 *   <li>状态/优先级/通道字段使用枚举类型替代 String
 *   <li>不继承 {@code MpBaseEntity}，审计字段平铺定义
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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

  public void markAsSending() {
    validateTransition(MessageStatusEnum.SENDING);
    this.status = MessageStatusEnum.SENDING;
  }

  public void markAsSuccess(String providerTraceId, long costMs, BigDecimal cost) {
    validateTransition(MessageStatusEnum.SUCCESS);
    this.status = MessageStatusEnum.SUCCESS;
    this.providerTraceId = providerTraceId;
    this.costMs = costMs;
    this.cost = cost;
  }

  public void markAsFailed(String errorMessage) {
    validateTransition(MessageStatusEnum.FAILED);
    this.status = MessageStatusEnum.FAILED;
    this.errorMessage = errorMessage;
  }

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

  public void markAsRecalled() {
    validateTransition(MessageStatusEnum.RECALLED);
    this.status = MessageStatusEnum.RECALLED;
    this.recallStatus = RecallStatusEnum.RECALLED;
    this.recallAt = LocalDateTime.now();
  }

  public void markAsSkipped() {
    validateTransition(MessageStatusEnum.SKIPPED);
    this.status = MessageStatusEnum.SKIPPED;
  }

  public boolean canTransitionTo(MessageStatusEnum targetStatus) {
    if (this.status == null) {
      return true;
    }
    return this.status.canTransitTo(targetStatus);
  }

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
