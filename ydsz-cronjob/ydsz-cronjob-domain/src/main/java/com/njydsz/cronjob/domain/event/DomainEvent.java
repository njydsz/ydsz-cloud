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
 * <p><b>与 ydsz-common-event 的边界（ADR-8，见 docs/architecture/adr/ADR-009-public-capability-convergence.md）：</b>
 * common-event {@code api.DomainEvent} 是<b>模块间集成事件契约</b>（继承 Spring {@code ApplicationEvent}，
 * 由 {@code DomainEventPublisher} 发布，供其他模块订阅）；本接口是<b>聚合内 Event Sourcing 事件</b>
 * （携带 {@code aggregateId}/{@code aggregateType}/事件溯源元数据，用于事件存储与状态重建）。
 * 二者语义不同层，故并存：跨模块通知请使用 common-event 契约（参见
 * {@code AlertDispatcher} / {@code DefaultTaskDispatcher} 的用法），聚合内状态变更溯源使用本接口。
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
