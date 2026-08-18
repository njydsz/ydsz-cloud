package com.njydsz.message.domain.entity.core;

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
 * <p>对应数据库表 {@code ydsz_msg_log}，是消息中心的核心事实表。每条消息从创建到最终送达
 * （或失败/死信）的完整生命周期记录均存储在此表中，支持优先级排队、消息聚合、撤回、
 * 回执追踪、渠道路由、灰度发布、重试调度等高级能力。
 *
 * <p>与 {@code MsgLogDO} 的区别：
 *
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

  /** 主键 ID（雪花算法） */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** 创建人 ID */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 ID */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 删除标识: false 未删除 / true 已删除 */
  private Boolean deleted;

  // ===== 业务字段 =====

  /** 发送通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU */
  private MessageChannelEnum channel;

  /** 业务类型 */
  private String bizType;

  /** 业务单据 ID */
  private String bizId;

  /** 接收人（脱敏存储） */
  private String receiver;

  /** 模板编码 */
  private String templateCode;

  /** 模板参数 JSON */
  private String templateParams;

  /** 发送内容(渲染后) */
  private String content;

  /** 发送状态: PENDING/SENDING/SUCCESS/FAILED/RETRY/DEAD/RECALLED/SCHEDULED/SKIPPED */
  private MessageStatusEnum status;

  /** 错误信息 */
  private String errorMessage;

  /** 发送优先级: LOW/NORMAL/HIGH/URGENT */
  private MessagePriorityEnum priority;

  /** 触发发送的用户 ID(系统发送为 SYSTEM) */
  private String senderId;

  /** 聚合组(同组消息可合并为摘要发送) */
  private String messageGroup;

  /** 聚合批次 ID */
  private String batchId;

  /** 命中的路由规则 ID */
  private String routeRuleId;

  /** 是否灰度命中: 0 正式 / 1 灰度 */
  private Integer canary;

  /** 灰度实验键（命中时记录原始 canaryKey,用于 A/B 报表分组） */
  private String canaryKey;

  /** 幂等去重键 */
  private String dedupKey;

  /** 撤回状态: NONE 未撤回 / RECALLED 已撤回 */
  private RecallStatusEnum recallStatus;

  /** 撤回时间 */
  private LocalDateTime recallAt;

  /** 回执状态: NONE/DELIVERED/READ/CLICKED/FAILED/TIMEOUT */
  private ReceiptStatusEnum receiptStatus;

  /** 回执到达时间 */
  private LocalDateTime receiptAt;

  /** 已重试次数 */
  private Integer retryCount;

  /** 下次重试时间(退避调度) */
  private LocalDateTime nextRetryAt;

  /** 三方服务商回执 ID */
  private String providerTraceId;

  /** 发送耗时(毫秒) */
  private Long costMs;

  /** 发送成本(元) */
  private BigDecimal cost;

  /** 系统链路追踪 ID */
  private String traceId;

  /** RocketMQ 消息 ID */
  private String msgId;

  /** RocketMQ Topic(DLQ 消息填充原 Topic) */
  private String topic;

  /** RocketMQ 重试次数 */
  private Integer reconsumeTimes;

  /** 父消息 ID(级联发送时自动填充) */
  private String parentMsgId;

  /** 定时发送时间(非空时 status=SCHEDULED) */
  private LocalDateTime scheduledAt;

  // ===== 领域行为 =====

  /**
   * 标记消息为发送中。
   *
   * <p>状态流转：PENDING/SCHEDULED/RETRY → SENDING。
   *
   * @throws IllegalStateException 如果当前状态不允许流转到 SENDING
   */
  public void markAsSending() {
    validateTransition(MessageStatusEnum.SENDING);
    this.status = MessageStatusEnum.SENDING;
  }

  /**
   * 标记消息为发送成功。
   *
   * <p>状态流转：SENDING/RETRY → SUCCESS。
   *
   * @param providerTraceId 三方服务商回执 ID
   * @param costMs 发送耗时（毫秒）
   * @param cost 发送成本（元）
   * @throws IllegalStateException 如果当前状态不允许流转到 SUCCESS
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
   * <p>状态流转：PENDING/SCHEDULED/SENDING → FAILED。
   *
   * @param errorMessage 错误信息
   * @throws IllegalStateException 如果当前状态不允许流转到 FAILED
   */
  public void markAsFailed(String errorMessage) {
    validateTransition(MessageStatusEnum.FAILED);
    this.status = MessageStatusEnum.FAILED;
    this.errorMessage = errorMessage;
  }

  /**
   * 标记消息为重试中。
   *
   * <p>状态流转：SENDING → RETRY。
   *
   * @param nextRetryAt 下次重试时间
   * @throws IllegalStateException 如果当前状态不允许流转到 RETRY
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
   * <p>状态流转：PENDING/SCHEDULED/SENDING/SUCCESS → RECALLED。
   *
   * @throws IllegalStateException 如果当前状态不允许流转到 RECALLED
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
   * <p>状态流转：PENDING/SCHEDULED/SENDING → SKIPPED。
   *
   * @throws IllegalStateException 如果当前状态不允许流转到 SKIPPED
   */
  public void markAsSkipped() {
    validateTransition(MessageStatusEnum.SKIPPED);
    this.status = MessageStatusEnum.SKIPPED;
  }

  /**
   * 判断当前状态是否可以流转到目标状态。
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
   * 判断当前状态是否为终态。
   *
   * @return true 表示终态（FAILED/DEAD/RECALLED/SKIPPED）
   */
  public boolean isTerminal() {
    if (this.status == null) {
      return false;
    }
    return this.status.isTerminal();
  }

  /**
   * 校验状态流转是否合法，不合法则抛出异常。
   *
   * @param targetStatus 目标状态
   * @throws IllegalStateException 如果流转不合法
   */
  private void validateTransition(MessageStatusEnum targetStatus) {
    if (this.status != null && !this.status.canTransitTo(targetStatus)) {
      throw new IllegalStateException(
          String.format("状态流转非法: %s → %s", this.status, targetStatus));
    }
  }
}
