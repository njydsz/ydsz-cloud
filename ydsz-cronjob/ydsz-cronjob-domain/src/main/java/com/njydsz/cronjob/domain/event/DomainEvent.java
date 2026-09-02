package com.njydsz.cronjob.domain.event;

import java.time.LocalDateTime;

/**
 * 领域事件基类接口（P3-1 Event Sourcing）。
 *
 * <p>所有实现 Event Sourcing 的领域事件需继承此接口，保证事件具有统一元数据：
 *
 * <ul>
 *   <li>{@link #eventId} — 事件唯一标识（雪花算法）
 *   <li>{@link #aggregateId} — 聚合根 ID（如 jobId）
 *   <li>{@link #aggregateType} — 聚合根类型（如 "job"）
 *   <li>{@link #eventType} — 事件类型（如 "JOB_CREATED"）
 *   <li>{@link #occurredAt} — 事件发生时间
 * </ul>
 *
 * <h3>使用场景</h3>
 *
 * <p>领域状态变更时产生事件，事件追加到事件存储（Event Store），用于：
 *
 * <ul>
 *   <li>审计追溯：完整记录"谁、何时、做了什么"
 *   <li>状态重建：通过回放事件重建聚合根状态
 *   <li>事件驱动：其他有界上下文订阅事件实现最终一致性
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DomainEvent {

  /**
   * 事件唯一标识。
   *
   * @return 事件 ID（雪花算法生成）
   */
  String eventId();

  /**
   * 聚合根 ID（产生事件的实体 ID）。
   *
   * @return 聚合根 ID
   */
  String aggregateId();

  /**
   * 聚合根类型。
   *
   * <p>用于事件路由和分类查询，如 "job"、"dag_definition"。
   *
   * @return 聚合根类型名称
   */
  String aggregateType();

  /**
   * 事件类型。
   *
   * <p>如 "JOB_CREATED"、"JOB_PAUSED"、"JOB_TRIGGERED"。
   *
   * @return 事件类型
   */
  String eventType();

  /**
   * 事件发生时间。
   *
   * @return 事件发生时间
   */
  LocalDateTime occurredAt();
}
