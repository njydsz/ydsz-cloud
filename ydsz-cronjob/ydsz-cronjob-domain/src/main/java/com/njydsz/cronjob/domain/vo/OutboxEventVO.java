package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

import com.njydsz.cronjob.domain.entity.OutboxEvent;

/**
 * Outbox 事件视图对象。
 *
 * <p>领域层 VO，对应 infra 实体 {@link OutboxEvent}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class OutboxEventVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 事件 ID */
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
