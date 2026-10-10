package com.njydsz.cronjob.domain.entity.event;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.jdbc.entity.MpBaseIdEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 存储事件实体（P3-1 Event Sourcing）。
 *
 * <p>对应 <code>ydsz_job_event_store</code> 表，存储所有领域事件的仅追加日志。
 *
 * <h3>表结构</h3>
 *
 * <pre>{@code
 * CREATE TABLE ydsz_job_event_store (
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
 * <p><b>表特征：</b>事件源表无租户/审计人字段，仅含 id 主键，继承 {@link MpBaseIdEntity}。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_event_store")
public class StoredEvent extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

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
}
