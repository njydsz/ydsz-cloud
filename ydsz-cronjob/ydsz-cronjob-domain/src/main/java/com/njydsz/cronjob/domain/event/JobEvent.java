package com.njydsz.cronjob.domain.event;

import java.time.LocalDateTime;

/**
 * 任务领域事件（P3-1 Event Sourcing）。
 *
 * <p>记录任务生命周期中的所有状态变更事件。每个事件不可变，追加写入事件存储。
 *
 * <h3>事件类型</h3>
 *
 * <ul>
 *   <li>{@link Type#CREATED} — 任务创建
 *   <li>{@link Type#UPDATED} — 任务配置更新
 *   <li>{@link Type#STATUS_CHANGED} — 状态变更（NORMAL ↔ PAUSED）
 *   <li>{@link Type#TRIGGERED} — 任务被触发执行
 *   <li>{@link Type#DELETED} — 任务删除
 *   <li>{@link Type#MIGRATED} — 任务集群漂移
 * </ul>
 *
 * @param eventId 事件唯一标识
 * @param jobId 任务 ID（聚合根 ID）
 * @param eventType 事件类型
 * @param payload 事件负载 JSON（事件详细数据）
 * @param operator 操作人
 * @param occurredAt 事件发生时间
 * @author ydsz-team
 * @since 26.09.01
 */
public record JobEvent(
    String eventId,
    String jobId,
    String eventType,
    String payload,
    String operator,
    LocalDateTime occurredAt)
    implements DomainEvent {

  /** 聚合根类型常量 */
  public static final String AGGREGATE_TYPE = "job";

  @Override
  public String aggregateId() {
    return jobId;
  }

  @Override
  public String aggregateType() {
    return AGGREGATE_TYPE;
  }

  /** 任务事件类型枚举 */
  public enum Type {
    /** 任务创建 */
    CREATED,
    /** 任务配置更新 */
    UPDATED,
    /** 状态变更 */
    STATUS_CHANGED,
    /** 任务被触发执行 */
    TRIGGERED,
    /** 任务删除 */
    DELETED,
    /** 任务集群漂移 */
    MIGRATED;

    /**
     * 安全解析事件类型字符串。
     *
     * @param value 类型字符串
     * @return 枚举值；无效值返回 null
     */
    public static Type parse(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      try {
        return Type.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
  }
}
