package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.infra.entity.FlowAuditLog;

/**
 * 流程审计日志 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_audit_log</code>，记录审批全操作轨迹。
 *
 * <p>审计日志是「不可变」的事实表（仅插入不更新/删除），用于安全审计/合规追溯/异常排查。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_instance_id — 流程实例维度查询索引
 *   <li>idx_audit_at — 操作时间排序索引（按时间范围查询）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.infra.entity.FlowAuditLog 审计日志实体
 * @see com.njydsz.workflow.server.service.FlowAuditService 审计 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowAuditLogMapper extends BaseMapper<FlowAuditLog> {

  /**
   * 查某实例的全部审计日志（按时间正序）
   *
   * @param instanceId 流程实例 ID
   * @return 审计日志列表（按时间正序）
   */
  List<FlowAuditLog> selectByInstanceId(@Param("instanceId") String instanceId);

  /**
   * 查某任务的操作记录
   *
   * @param taskId 任务 ID
   * @return 任务审计日志列表
   */
  List<FlowAuditLog> selectByTaskId(@Param("taskId") String taskId);

  /**
   * 查某操作人的审计日志（P1-8: 加签历史查询）
   *
   * <p>P3: {@code startTime} 为必填项，强制限定查询时间范围，避免跨分区全表扫描。 服务层应保证非空（默认近 12 个月）。
   *
   * @param operatorId 操作人 ID
   * @param startTime 操作时间下界（含，必填）
   * @param endTime 操作时间上界（含，可选）
   * @return 审计日志列表
   */
  List<FlowAuditLog> selectByOperatorId(
      @Param("operatorId") String operatorId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime);

  /**
   * 查某目标人（转办/委派/加签目标）的审计日志（P1-8: 加签历史查询）
   *
   * <p>P3: {@code startTime} 为必填项，强制限定查询时间范围，避免跨分区全表扫描。
   *
   * @param targetId 目标人 ID
   * @param startTime 操作时间下界（含，必填）
   * @param endTime 操作时间上界（含，可选）
   * @return 审计日志列表
   */
  List<FlowAuditLog> selectByTargetId(
      @Param("targetId") String targetId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime);

  /**
   * 分页查询流程实例的加签历史记录（P1-8: 数据库级分页，避免内存分页 OOM 风险）。
   *
   * <p>按 action IN (加签动作列表) + instance_id 过滤，支持 LIMIT/OFFSET 分页。
   *
   * @param instanceId 流程实例 ID
   * @param actions 加签动作列表（如 COUNTERSIGN_BEFORE / COUNTERSIGN_AFTER / COUNTERSIGN_PARALLEL / COUNTERSIGN_REMOVE）
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 审计日志列表
   */
  List<FlowAuditLog> selectCountersignByInstance(
      @Param("instanceId") String instanceId,
      @Param("actions") List<String> actions,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 统计流程实例的加签历史记录总数（P1-8: 配合分页查询返回 total）。
   *
   * @param instanceId 流程实例 ID
   * @param actions 加签动作列表
   * @return 符合条件的记录总数
   */
  long countCountersignByInstance(
      @Param("instanceId") String instanceId,
      @Param("actions") List<String> actions);
}
