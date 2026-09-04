package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.domain.entity.FlowTimer;

/**
 * 工作流定时器 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_timer</code>，存储工作流中的定时器配置（超时/催办/自动跳过）。
 *
 * <p>定时器由 {@code FlowTimerScheduler} 周期性扫描触发（每分钟），执行超时自动通过/催办通知/自动跳过等动作。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_timer_id — 定时器 ID 唯一索引
 *   <li>idx_fire_time — 触发时间排序索引（扫描待触发定时器）
 *   <li>idx_status — 状态过滤索引（PENDING/FIRED/CANCELLED）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.domain.entity.FlowTimer 定时器实体
 * @see com.njydsz.workflow.server.scheduler.FlowTimerScheduler 定时器调度器
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowTimerMapper extends BaseMapper<FlowTimer> {

  /**
   * 扫描到点的 PENDING 定时器（status = PENDING AND fire_at <= now AND deleted = 0）
   *
   *
   * @param now 当前时间（用于判断定时器是否到期）
   * @param limit 返回条数上限
   * @return 到期待触发的定时器列表
   */
  List<FlowTimer> selectDueTimers(@Param("now") LocalDateTime now, @Param("limit") int limit);

  /**
   * 关闭某 userTask 关联的所有 BOUNDARY 定时器（CANCELLED）
   *
   * @param boundaryTaskId userTask ID
   * @param reason 取消原因
   * @return 受影响行数
   */
  int cancelByTask(@Param("boundaryTaskId") String boundaryTaskId, @Param("reason") String reason);

  /**
   * 标记定时器已触发
   *
   * @param id 定时器 ID
   * @param firedAt 触发时间
   * @return 受影响行数
   */
  int markFired(@Param("id") String id, @Param("firedAt") LocalDateTime firedAt);

  /**
   * 关闭某实例所有 PENDING 定时器（实例终止/驳回时使用）
   *
   * @param instanceId 流程实例 ID
   * @param reason 取消原因
   * @return 受影响行数
   */
  int cancelByInstance(@Param("instanceId") String instanceId, @Param("reason") String reason);

  /**
   * 统计实例的 PENDING 定时器数（用于检查流程是否被定时器阻塞）
   *
   * @param instanceId 流程实例 ID
   * @return PENDING 状态定时器数量
   */
  long countPendingByInstance(@Param("instanceId") String instanceId);
}
