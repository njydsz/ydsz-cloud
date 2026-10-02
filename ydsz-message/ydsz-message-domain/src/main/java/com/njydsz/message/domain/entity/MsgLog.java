package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 消息发送日志领域实体，全通道发送全量记录的事实表。
 *
 * <p>对应数据库表 {@code ydsz_msg_log}。记录每条消息的完整生命周期（接收→校验→路由→渲染→投递→回执），
 * 状态/优先级/通道字段使用枚举类型。含领域行为方法（markAsSending/Success/Failed/Recalled/Skipped）
 * 驱动状态流转，通过 canTransitionTo 校验合法性。
 *
 * <p><b>status 字段说明：</b>DB 存储为 VARCHAR（枚举 name 字符串，如 PENDING/SENDING/SUCCESS），
 * 领域方法通过 {@link MessageStatusEnum#valueOf(String)} 做枚举转换，保证类型安全与兼容基类 {@code MpBaseEntity}。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合 JPA 继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MsgLog extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  // ===== 业务字段 =====
  private MessageChannelEnum channel;
  private String bizType;
  private String bizId;
  private String receiver;
  private String templateCode;
  private String templateParams;
  private String content;
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
   * 获取状态枚举视图。
   *
   * @return 状态枚举，无法解析时返回 null
   */
  public MessageStatusEnum getStatusEnum() {
    return getStatus() == null ? null : MessageStatusEnum.valueOf(getStatus());
  }

  /**
   * 设置状态（通过枚举）。
   *
   * @param statusEnum 状态枚举，不可为 null
   */
  public void setStatusEnum(MessageStatusEnum statusEnum) {
    if (statusEnum == null) {
      setStatus(null);
      return;
    }
    setStatus(statusEnum.name());
  }

  /**
   * 标记消息为发送中状态。
   *
   * <p>状态流转：待发送/重试 → 发送中
   *
   * @throws IllegalStateException 当当前状态不允许流转到发送中时
   */
  public void markAsSending() {
    validateTransition(MessageStatusEnum.SENDING);
    setStatus(MessageStatusEnum.SENDING.name());
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
    setStatus(MessageStatusEnum.SUCCESS.name());
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
    setStatus(MessageStatusEnum.FAILED.name());
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
    setStatus(MessageStatusEnum.RETRY.name());
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
    setStatus(MessageStatusEnum.RECALLED.name());
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
    setStatus(MessageStatusEnum.SKIPPED.name());
  }

  /**
   * 判断消息是否可流转到目标状态。
   *
   * @param targetStatus 目标状态
   * @return true 表示允许流转
   */
  public boolean canTransitionTo(MessageStatusEnum targetStatus) {
    MessageStatusEnum current = getStatusEnum();
    if (current == null) {
      return true;
    }
    return current.canTransitTo(targetStatus);
  }

  /**
   * 判断消息是否处于终态（成功/失败/已撤回/已跳过）。
   *
   * <p>终态消息不再参与任何状态流转。
   *
   * @return true 表示已处于终态
   */
  public boolean isTerminal() {
    MessageStatusEnum current = getStatusEnum();
    if (current == null) {
      return false;
    }
    return current.isTerminal();
  }

  private void validateTransition(MessageStatusEnum targetStatus) {
    MessageStatusEnum current = getStatusEnum();
    if (current != null && !current.canTransitTo(targetStatus)) {
      throw new IllegalStateException(
          I18n.message("message.msglog.invalid_transition", new Object[]{current, targetStatus}));
    }
  }
}
