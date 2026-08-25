package com.njydsz.message.infra.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 聚合批次表: 同 aggregate_group+receiver 的消息按频率合并为摘要发送
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_aggregate")
public class MsgAggregate extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 聚合组 */
  private String aggregateGroup;

  /** 接收人 */
  private String receiver;

  /** 通道 */
  private String channel;

  /** 批次状态: PENDING 攒批中 / READY 就绪待发 / SENT 已发送 / CANCELLED 已取消 */
  private String batchStatus;

  /** 消息数量 */
  private Integer messageCount;

  /** 首条消息时间 */
  private LocalDateTime firstMessageAt;

  /** 末条消息时间 */
  private LocalDateTime lastMessageAt;

  /** 计划发送时间(到达后触发摘要发送) */
  private LocalDateTime scheduledSendAt;

  /** 实际发送时间 */
  private LocalDateTime sentAt;

  /** 聚合后摘要内容(渲染后) */
  private String digestContent;
}
