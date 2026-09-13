package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * Outbox 事件领域实体 — 事务性 Outbox 模式。
 *
 * <p>对应数据库表 {@code ydsz_msg_outbox}，继承 {@link MpBaseIdEntity} 仅保留 ID 与审计字段，
 * 业务字段平铺定义。与 {@code OutboxEventEntity} 的区别：
 * <ul>
 *   <li>类名不带 {@code Entity} 后缀，与 Mapper XML resultMap type 对齐</li>
 *   <li>使用 {@code @SuperBuilder} + {@code @NoArgsConstructor} + {@code @AllArgsConstructor}，
 *       与 {@code MsgTrace} 等已有实体保持一致</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 泛型擦除导致 unchecked 警告
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_outbox")
public class OutboxEvent extends MpBaseIdEntity<String> {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 聚合根类型 */
  private String aggregateType;

  /** 聚合根 ID */
  private String aggregateId;

  /** 事件类型 */
  private String eventType;

  /** 事件负载 JSON */
  private String payload;

  /** 租户 ID（多租户隔离） */
  private String tenantId;

  /** 发布时间 */
  private LocalDateTime publishedAt;

  /** 发布尝试次数 */
  private Integer publishAttempts;

  /** 发布状态: PENDING / PUBLISHING / PUBLISHED / FAILED */
  private String status;
}
