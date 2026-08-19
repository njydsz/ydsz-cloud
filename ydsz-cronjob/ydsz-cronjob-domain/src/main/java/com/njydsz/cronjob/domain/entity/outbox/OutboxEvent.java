package com.njydsz.cronjob.domain.entity.outbox;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbox 事件实体（P0-2：事务性 Outbox 事件模式）。
 *
 * <p>事件先写入此表（与业务操作同一事务），再由 {@link OutboxPublisher} 异步扫描并发布到外部系统
 * （WebHook / MQ / 事件总线）。保证"至少一次"投递语义，避免业务操作与事件发布的一致性问题。
 *
 * <h3>表名</h3>
 *
 * <p>{@code ydsz_job_outbox}
 *
 * <h3>字段说明</h3>
 *
 * <ul>
 *   <li>{@code id}：自增主键
 *   <li>{@code eventKey}：事件唯一键（幂等去重，如 jobKey + logId + eventType）
 *   <li>{@code eventType}：事件类型枚举（JOB_STARTED / JOB_SUCCESS / JOB_FAILED / JOB_TIMEOUT / 
 *       DAG_STARTED / DAG_COMPLETED）
 *   <li>{@code topic}：投递目标主题（webhook / metrics / audit）
 *   <li>{@code payload}：事件负载 JSON
 *   <li>{@code status}：发布状态（PENDING / PUBLISHED / DEAD）
 *   <li>{@code retryCount}：已重试次数
 *   <li>{@code nextRetryTime}：下次重试时间
 *   <li>{@code createTime}：创建时间
 *   <li>{@code updateTime}：更新时间
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonClass(description = "Outbox 事件实体，标记可安全反序列化")
@TableName("ydsz_job_outbox")
public class OutboxEvent {

  /** 事件 ID */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 事件唯一键（幂等去重） */
  @TableField("event_key")
  @JsonProperty("eventKey")
  private String eventKey;

  /** 事件类型 */
  @TableField("event_type")
  @JsonProperty("eventType")
  private EventType eventType;

  /** 投递目标主题 */
  @TableField("topic")
  private String topic;

  /** 事件负载 JSON */
  @TableField("payload")
  private String payload;

  /** 发布状态 */
  @TableField("status")
  private OutboxStatus status;

  /** 已重试次数 */
  @TableField("retry_count")
  @JsonProperty("retryCount")
  private Integer retryCount;

  /** 下次重试时间 */
  @TableField("next_retry_time")
  @JsonProperty("nextRetryTime")
  private LocalDateTime nextRetryTime;

  /** 创建时间 */
  @TableField("create_time")
  @JsonProperty("createTime")
  private LocalDateTime createTime;

  /** 更新时间 */
  @TableField("update_time")
  @JsonProperty("updateTime")
  private LocalDateTime updateTime;

  /**
   * 事件类型枚举。
   *
   * <p>定义任务生命周期各阶段事件。
   */
  public enum EventType {
    /** 任务开始执行 */
    JOB_STARTED("JOB_STARTED"),
    /** 任务执行成功 */
    JOB_SUCCESS("JOB_SUCCESS"),
    /** 任务执行失败 */
    JOB_FAILED("JOB_FAILED"),
    /** 任务执行超时 */
    JOB_TIMEOUT("JOB_TIMEOUT"),
    /** DAG 工作流开始 */
    DAG_STARTED("DAG_STARTED"),
    /** DAG 工作流完成 */
    DAG_COMPLETED("DAG_COMPLETED");

    @EnumValue
    private final String value;

    EventType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /**
   * Outbox 事件状态。
   */
  public enum OutboxStatus {
    /** 待发布 */
    PENDING("PENDING"),
    /** 已发布 */
    PUBLISHED("PUBLISHED"),
    /** 死亡信（重试耗尽） */
    DEAD("DEAD");

    @EnumValue
    private final String value;

    OutboxStatus(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
