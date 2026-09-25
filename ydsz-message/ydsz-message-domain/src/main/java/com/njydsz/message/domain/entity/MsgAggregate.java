package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息聚合批次实体，记录同一接收人在同一聚合组内攒批待发的摘要发送状态。
 *
 * <p>对应数据库表 {@code ydsz_msg_aggregate}。通过 aggregate_group + receiver 唯一聚合，
 * 当多条消息命中同一聚合窗口时合并为一条摘要发送，减少高频推送对用户的打扰。
 * 生命周期：PENDING（攒批中）→ READY（就绪待发）→ SENT（已发送）/ CANCELLED（已取消）。
 *
 * @author ydsz
 * @since 26.09.24
 */// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合泛型父类继承，Builder 返回原始父类类型
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
