package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 聚合批次视图对象（VO）。
 *
 * <p>用于 Controller 层返回消息聚合发送的完整信息，将同一接收人同一通道的 多条消息聚合为一条摘要消息发送，减少打扰并提升信息密度。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgAggregateVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 聚合记录唯一标识（主键） */
  private String id;

  /** 聚合分组键 */
  private String aggregateGroup;

  /** 接收人 */
  private String receiver;

  /** 通道 */
  private String channel;

  /** 批次状态（PENDING/SENDING/SENT/FAILED） */
  private String batchStatus;

  /** 聚合消息数 */
  private Integer messageCount;

  /** 首条消息时间 */
  private LocalDateTime firstMessageAt;

  /** 末条消息时间 */
  private LocalDateTime lastMessageAt;

  /** 计划发送时间 */
  private LocalDateTime scheduledSendAt;

  /** 实际发送时间 */
  private LocalDateTime sentAt;

  /** 摘要内容 */
  private String digestContent;

  /** 状态（ACTIVE/EXPIRED/SENT） */
  private String status;

  /** 租户 ID */
  private String tenantId;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
