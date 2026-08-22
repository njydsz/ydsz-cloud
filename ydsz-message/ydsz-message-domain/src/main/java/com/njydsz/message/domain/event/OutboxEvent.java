package com.njydsz.message.domain.event;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

/**
 * Outbox 事件实体（领域层）。
 *
 * <p>实现事务性 Outbox 模式：领域事件先落库到 Outbox 表，再通过异步扫描器发布到 Spring 事件总线。
 * 保证事件发布的 at-least-once 语义，即使应用崩溃也能从 Outbox 表恢复未发布的事件。
 *
 * <p>Outbox 表结构（infra 层建表）：
 * <pre>
 * CREATE TABLE ydsz_msg_outbox (
 *   id VARCHAR(64) PRIMARY KEY,
 *   aggregate_type VARCHAR(128) NOT NULL,
 *   aggregate_id VARCHAR(128) NOT NULL,
 *   event_type VARCHAR(128) NOT NULL,
 *   payload TEXT NOT NULL,
 *   tenant_id VARCHAR(64),
 *   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
 *   published_at TIMESTAMP,
 *   publish_attempts INT NOT NULL DEFAULT 0,
 *   status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
 * );
 * CREATE INDEX idx_outbox_status_created ON ydsz_msg_outbox (status, created_at);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class OutboxEvent implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 事件唯一 ID */
  private String id = UUID.randomUUID().toString();

  /** 聚合根类型（如：Message、Template、Batch） */
  private String aggregateType;

  /** 聚合根 ID */
  private String aggregateId;

  /** 事件类型（如：MessageSent、MessageStatusChanged） */
  private String eventType;

  /** 事件负载（JSON 序列化） */
  private String payload;

  /** 租户 ID */
  private String tenantId;

  /** 创建时间 */
  private LocalDateTime createdAt = LocalDateTime.now();

  /** 发布时间（已发布时填写） */
  private LocalDateTime publishedAt;

  /** 发布尝试次数（超过阈值标记为 FAILED） */
  private int publishAttempts;

  /** 发布状态：PENDING / PUBLISHING / PUBLISHED / FAILED */
  private String status = "PENDING";

  /**
   * 创建 Outbox 事件。
   *
   * @param aggregateType 聚合根类型
   * @param aggregateId 聚合根 ID
   * @param eventType 事件类型
   * @param payload 事件负载 JSON
   * @param tenantId 租户 ID
   */
  public OutboxEvent(
      String aggregateType,
      String aggregateId,
      String eventType,
      String payload,
      String tenantId) {
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.tenantId = tenantId;
    this.createdAt = LocalDateTime.now();
    this.publishAttempts = 0;
    this.status = "PENDING";
  }

  /** 默认构造器（序列化用）。 */
  public OutboxEvent() {
    this.createdAt = LocalDateTime.now();
    this.publishAttempts = 0;
    this.status = "PENDING";
  }

  /**
   * 标记为发布中。
   */
  public void markPublishing() {
    this.status = "PUBLISHING";
    this.publishAttempts++;
  }

  /**
   * 标记为已发布。
   */
  public void markPublished() {
    this.status = "PUBLISHED";
    this.publishedAt = LocalDateTime.now();
  }

  /**
   * 标记为发布失败。
   *
   * @param maxRetries 最大重试次数
   */
  public void markFailed(int maxRetries) {
    if (this.publishAttempts >= maxRetries) {
      this.status = "FAILED";
    } else {
      this.status = "PENDING";
    }
  }
}
