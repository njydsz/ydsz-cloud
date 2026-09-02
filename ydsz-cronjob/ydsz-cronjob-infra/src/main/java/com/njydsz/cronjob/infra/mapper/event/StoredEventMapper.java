package com.njydsz.cronjob.infra.mapper.event;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.cronjob.infra.entity.event.StoredEvent;

/**
 * 存储事件 Mapper（P3-1 Event Sourcing）。
 *
 * <p>对应 <code>ydsz_event_store</code> 表。仅提供查询能力，写入由 Repository 通过
 * MyBatis-Plus 基类方法完成。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface StoredEventMapper extends BaseMapper<StoredEvent> {

  /**
   * 按聚合根 ID 查询事件流（按发生时间升序）。
   *
   * @param aggregateId 聚合根 ID
   * @return 事件列表
   */
  @Select(
      "SELECT id, aggregate_type, aggregate_id, event_type, payload, operator, occurred_at, created_at "
          + "FROM ydsz_event_store "
          + "WHERE aggregate_type = 'job' AND aggregate_id = #{aggregateId} "
          + "ORDER BY occurred_at ASC")
  List<StoredEvent> selectByAggregateId(@Param("aggregateId") String aggregateId);

  /**
   * 按聚合根 ID 和时间范围查询事件流。
   *
   * @param aggregateId 聚合根 ID
   * @param startTime 开始时间（含）
   * @param endTime 结束时间（含）
   * @return 事件列表
   */
  @Select(
      "<script>"
          + "SELECT id, aggregate_type, aggregate_id, event_type, payload, operator, occurred_at, created_at "
          + "FROM ydsz_event_store "
          + "WHERE aggregate_type = 'job' AND aggregate_id = #{aggregateId} "
          + "<if test=\"startTime != null\"> AND occurred_at &gt;= #{startTime} </if> "
          + "<if test=\"endTime != null\"> AND occurred_at &lt;= #{endTime} </if> "
          + "ORDER BY occurred_at ASC"
          + "</script>")
  List<StoredEvent> selectByAggregateIdAndTimeRange(
      @Param("aggregateId") String aggregateId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime);

  /**
   * 按事件类型分页查询（按 occurredAt 降序）。
   *
   * @param eventType 事件类型（null 表示全部）
   * @param limit 每页条数
   * @param offset 偏移量
   * @return 事件列表
   */
  @Select(
      "<script>"
          + "SELECT id, aggregate_type, aggregate_id, event_type, payload, operator, occurred_at, created_at "
          + "FROM ydsz_event_store "
          + "WHERE aggregate_type = 'job' "
          + "<if test=\"eventType != null and eventType != ''\"> AND event_type = #{eventType} </if> "
          + "ORDER BY occurred_at DESC "
          + "LIMIT #{limit} OFFSET #{offset}"
          + "</script>")
  List<StoredEvent> selectByType(
      @Param("eventType") String eventType,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /**
   * 统计指定类型的事件总数。
   *
   * @param eventType 事件类型（null 表示全部）
   * @return 总数
   */
  @Select(
      "<script>"
          + "SELECT COUNT(*) FROM ydsz_event_store "
          + "WHERE aggregate_type = 'job' "
          + "<if test=\"eventType != null and eventType != ''\"> AND event_type = #{eventType} </if> "
          + "</script>")
  long countByType(@Param("eventType") String eventType);
}
