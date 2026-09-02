package com.njydsz.cronjob.infra.entity.event;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 存储事件实体（P3-1 Event Sourcing）。
 *
 * <p>对应 <code>ydsz_event_store</code> 表，存储所有领域事件的仅追加日志。
 *
 * <h3>表结构</h3>
 *
 * <pre>{@code
 * CREATE TABLE ydsz_event_store (
 *   id              VARCHAR(32)  PRIMARY KEY COMMENT '事件 ID（雪花算法）',
 *   aggregate_type  VARCHAR(64)  NOT NULL COMMENT '聚合根类型（如 job）',
 *   aggregate_id    VARCHAR(32)  NOT NULL COMMENT '聚合根 ID',
 *   event_type      VARCHAR(64)  NOT NULL COMMENT '事件类型（如 JOB_CREATED）',
 *   payload         TEXT         COMMENT '事件负载 JSON',
 *   operator        VARCHAR(64)  COMMENT '操作人',
 *   occurred_at     TIMESTAMP    NOT NULL COMMENT '事件发生时间',
 *   created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '记录写入时间',
 *   INDEX idx_aggregate (aggregate_type, aggregate_id, occurred_at),
 *   INDEX idx_type_time (event_type, occurred_at)
 * ) COMMENT='事件存储表（Event Sourcing）';
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@TableName("ydsz_event_store")
public class StoredEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 事件 ID（雪花算法） */
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  /** 聚合根类型（如 job、dag_definition） */
  private String aggregateType;

  /** 聚合根 ID */
  private String aggregateId;

  /** 事件类型（如 JOB_CREATED） */
  private String eventType;

  /** 事件负载 JSON */
  private String payload;

  /** 操作人 */
  private String operator;

  /** 事件发生时间 */
  private LocalDateTime occurredAt;

  /** 记录写入时间 */
  private LocalDateTime createdAt;
}
