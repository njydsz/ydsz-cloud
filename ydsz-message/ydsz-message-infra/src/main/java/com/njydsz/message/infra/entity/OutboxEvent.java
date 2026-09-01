package com.njydsz.message.infra.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Outbox 事件数据库实体（Infra 层）。
 *
 * <p>对应 {@code ydsz_msg_outbox} 表，用于实现事务性 Outbox 模式。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@TableName("ydsz_msg_outbox")
public class OutboxEvent implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 事件唯一 ID */
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  /** 聚合根类型 */
  @TableField("aggregate_type")
  private String aggregateType;

  /** 聚合根 ID */
  @TableField("aggregate_id")
  private String aggregateId;

  /** 事件类型 */
  @TableField("event_type")
  private String eventType;

  /** 事件负载 JSON */
  @TableField("payload")
  private String payload;

  /** 租户 ID */
  @TableField("tenant_id")
  private String tenantId;

  /** 创建时间 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 发布时间 */
  @TableField("published_at")
  private LocalDateTime publishedAt;

  /** 发布尝试次数 */
  @TableField("publish_attempts")
  private Integer publishAttempts;

  /** 发布状态 */
  @TableField("status")
  private String status;
}
