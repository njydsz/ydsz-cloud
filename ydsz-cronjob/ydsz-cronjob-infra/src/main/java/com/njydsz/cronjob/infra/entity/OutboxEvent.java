package com.njydsz.cronjob.infra.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * Outbox 事件实体（事务性 Outbox 事件模式）。
 *
 * <p>对应 ydsz_job_outbox 表，存储待发布的领域事件。
 *
 * <p>调用方通过 {@link com.njydsz.cronjob.domain.repository.outbox.OutboxEventRepository}
 * 写入事件（在业务事务内），再由
 * {@link com.njydsz.cronjob.server.core.outbox.OutboxPublisher} 异步扫描并发布。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@TableName("ydsz_job_outbox")
public class OutboxEvent implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 事件 ID */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 事件 KEY（幂等去重标识） */
  private String eventKey;

  /** 事件类型 */
  private String eventType;

  /** 目标 topic（webhook / metrics / audit） */
  private String topic;

  /** 事件 payload（JSON 字符串） */
  private String payload;

  /** 事件状态 */
  private OutboxStatus status;

  /** 已重试次数 */
  private Integer retryCount;

  /** 下次重试时间 */
  private LocalDateTime nextRetryTime;

  /** 创建时间 */
  private LocalDateTime createTime;

  /** 更新时间 */
  private LocalDateTime updateTime;

  /**
   * Outbox 事件状态枚举。
   */
  public enum OutboxStatus {
    /** 待发布 */
    PENDING,
    /** 已发布 */
    PUBLISHED,
    /** 死亡信（重试耗尽） */
    DEAD
  }
}
