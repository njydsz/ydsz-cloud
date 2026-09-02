package com.njydsz.cronjob.domain.repository.event;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.event.JobEvent;

/**
 * 事件存储 Repository 接口（P3-1 Event Sourcing）。
 *
 * <p>定义事件追加、查询的契约。实现层位于 infra 模块。
 *
 * <p>事件存储为仅追加（append-only）模式，不支持修改和删除（清理过期事件除外）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface EventStoreRepository {

  /**
   * 追加一条领域事件到事件存储。
   *
   * <p>应在业务操作的事务内调用，保证业务数据与事件的原子性。
   *
   * @param event 待追加的事件（非空）
   * @return 追加后的事件（含生成的 ID）
   */
  JobEvent append(JobEvent event);

  /**
   * 按聚合根 ID 查询事件流（按发生时间升序）。
   *
   * <p>用于重建聚合根状态或审计追溯。
   *
   * @param aggregateId 聚合根 ID
   * @return 事件列表（按 occurredAt 升序）
   */
  List<JobEvent> findByAggregateId(String aggregateId);

  /**
   * 按聚合根 ID 和时间范围查询事件流。
   *
   * @param aggregateId 聚合根 ID
   * @param startTime 开始时间（含，null 表示不限）
   * @param endTime 结束时间（含，null 表示不限）
   * @return 事件列表（按 occurredAt 升序）
   */
  List<JobEvent> findByAggregateIdAndTimeRange(
      String aggregateId, LocalDateTime startTime, LocalDateTime endTime);

  /**
   * 按事件类型分页查询（全局视角）。
   *
   * @param eventType 事件类型（null 表示不限）
   * @param limit 每页条数
   * @param offset 偏移量
   * @return 事件列表（按 occurredAt 降序）
   */
  List<JobEvent> findByType(String eventType, int limit, int offset);

  /**
   * 统计指定类型的事件总数。
   *
   * @param eventType 事件类型（null 表示全部）
   * @return 事件总数
   */
  long countByType(String eventType);

  /**
   * 清理过期事件（删除指定时间之前的事件）。
   *
   * <p>用于定期清理历史数据，防止事件存储无限增长。
   *
   * @param beforeTime 清理阈值（删除此时间之前的事件）
   * @return 删除条数
   */
  int deleteBefore(LocalDateTime beforeTime);
}
