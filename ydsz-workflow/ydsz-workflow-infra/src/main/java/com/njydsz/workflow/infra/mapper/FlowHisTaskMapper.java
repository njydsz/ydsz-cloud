package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.domain.entity.FlowHisTask;

/**
 * 历史任务 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_his_task</code>，归档已完成的流程任务，供已办查询与审计追溯。
 *
 * <p>任务结束（同意/驳回/转办/加签完成）后从 {@code ydsz_flow_run_task} 迁移到本表，保留完整审批轨迹。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_task_id — 任务 ID 唯一索引（1:1 关联运行任务）
 *   <li>idx_user_done — 用户维度已办查询索引
 *   <li>idx_end_at — 完成时间排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.domain.entity.FlowHisTask 历史任务实体
 * @see com.njydsz.workflow.server.service.FlowTaskHistoryService 已办 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowHisTaskMapper extends BaseMapper<FlowHisTask> {

  /**
   * 查用户已办（历史）
   *
   * @param assigneeId 办理人用户 ID
   * @param tenantId 租户 ID
   * @return 已办任务列表
   */
  List<FlowHisTask> selectDoneByAssignee(
      @Param("assigneeId") String assigneeId, @Param("tenantId") String tenantId);

  /**
   * 查用户已办（历史，真分页：LIMIT/OFFSET）
   *
   *
   * @param assigneeId 办理人用户 ID
   * @param tenantId 租户 ID
   * @param offset 分页偏移量
   * @param limit 每页大小
   * @return 已办任务分页列表
   */
  List<FlowHisTask> selectDoneByAssigneePage(
      @Param("assigneeId") String assigneeId,
      @Param("tenantId") String tenantId,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 统计用户已办总数（用于分页计算总页数）
   *
   * @param assigneeId 办理人用户 ID
   * @param tenantId 租户 ID
   * @return 已办任务总数
   */
  long countDoneByAssignee(
      @Param("assigneeId") String assigneeId, @Param("tenantId") String tenantId);

  /**
   * 查某实例的所有历史
   *
   * @param instanceId 流程实例 ID
   * @return 历史任务列表
   */
  List<FlowHisTask> selectByInstanceId(@Param("instanceId") String instanceId);

  /**
   * P2-31: 按节点统计平均耗时（GROUP BY node_code, node_name）
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID（可空）
   * @return 每个节点一行统计：nodeCode, nodeName, avgDurationMs, count
   */
  List<Map<String, Object>> nodeDurationStats(
      @Param("flowCode") String flowCode, @Param("tenantId") String tenantId);

  /**
   * P2-33: 多维筛选已办分页查询（真分页：LIMIT/OFFSET）
   *
   *
   * @param assigneeId 办理人用户 ID
   * @param businessType 业务类型过滤（可空）
   * @param flowCode 流程编码过滤（可空）
   * @param startTime 完成时间下界（可空）
   * @param endTime 完成时间上界（可空）
   * @param tenantId 租户 ID
   * @param offset 分页偏移量
   * @param limit 每页大小
   * @return 已办任务分页列表
   */
  List<FlowHisTask> selectDonePage(
      @Param("assigneeId") String assigneeId,
      @Param("businessType") String businessType,
      @Param("flowCode") String flowCode,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("tenantId") String tenantId,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * P2-33: 多维筛选已办总数统计
   *
   * @param assigneeId 办理人用户 ID
   * @param businessType 业务类型过滤（可空）
   * @param flowCode 流程编码过滤（可空）
   * @param startTime 完成时间下界（可空）
   * @param endTime 完成时间上界（可空）
   * @param tenantId 租户 ID
   * @return 符合条件的已办总数
   */
  long countDone(
      @Param("assigneeId") String assigneeId,
      @Param("businessType") String businessType,
      @Param("flowCode") String flowCode,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("tenantId") String tenantId);

  /**
   * P1-1: 查询实例经过的历史节点（去重，按首次完成时间排序）， 用于驳回时让用户选择驳回到任意历史节点。
   *
   * @param instanceId 流程实例 ID
   * @return 节点列表：nodeCode / nodeName / firstFinishAt / assigneeName
   */
  List<Map<String, Object>> listPassedNodes(@Param("instanceId") String instanceId);

  /**
   * P1-5: 查询同实例下已审批过（task_status=COMPLETED）的办理人 ID 列表（去重）。
   *
   * <p>用于跨节点办理人去重：排除已审批过的人员，支持"一人多环节只审批一次"。 排除 assignee_id = '0'（SYSTEM_AUTO_PASS / SERVICE
   * 节点等系统生成的记录）。
   *
   * @param instanceId 流程实例 ID
   * @return 已审批过的办理人 ID 列表（去重）
   */
  List<String> selectCompletedAssigneeIds(@Param("instanceId") String instanceId);

  /**
   * P2-4: 按办理人分组聚合效率统计（SQL 层 GROUP BY，避免 Java 层全表加载）
   *
   * @param tenantId 租户 ID（可空）
   * @param startTime finish_at 下界（可空）
   * @param endTime finish_at 上界（可空）
   * @param limit 返回条数
   * @return 每个办理人一行：assigneeId / assigneeName / completedCount / avgDurationMs / totalDurationMs
   */
  List<Map<String, Object>> selectApproverEfficiency(
      @Param("tenantId") String tenantId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("limit") int limit);

  /**
   * P2-7: 流程效率对比 — 按流程编码分组聚合效率指标。
   *
   * <p>流程效率对比看板。聚合指标：
   *
   * <ul>
   *   <li>totalCount — 任务总数（COMPLETED + REJECTED）
   *   <li>completedCount — 通过数（COMPLETED）
   *   <li>rejectedCount — 驳回数（REJECTED）
   *   <li>rejectionRate — 驳回率 = rejectedCount / totalCount
   *   <li>avgDurationMs — 平均处理耗时（仅 COMPLETED）
   * </ul>
   *
   * @param tenantId 租户 ID（可空）
   * @param startTime finish_at 下界（可空）
   * @param endTime finish_at 上界（可空）
   * @return 每个流程一行：flowCode / flowName / totalCount / completedCount / rejectedCount /
   *     rejectionRate / avgDurationMs
   */
  List<Map<String, Object>> selectFlowEfficiencyComparison(
      @Param("tenantId") String tenantId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime);

  /**
   * P1-5: 单 SQL 聚合概览统计（替代多次 COUNT 查询，5 次 → 1 次）。
   *
   * <p>使用 PostgreSQL 条件聚合（COUNT ... FILTER）一次性返回： totalTasks / completedTasks / rejectedTasks /
   * rejectionRate / avgDurationMs
   *
   * @param tenantId 租户 ID（可空）
   * @param startTime finish_at 下界（可空）
   * @param endTime finish_at 上界（可空）
   * @return 单行统计结果 Map
   */
  Map<String, Object> selectOverviewStats(
      @Param("tenantId") String tenantId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime);

  /**
   * P1-5: 审批趋势 — 按时间粒度分组聚合（date_trunc）。
   *
   * @param tenantId 租户 ID（可空）
   * @param startTime finish_at 下界（可空）
   * @param endTime finish_at 上界（可空）
   * @param granularity 时间粒度：day / week / month
   * @return 每个时间粒度一行：date / totalCount / completedCount / rejectedCount / avgDurationMs
   */
  List<Map<String, Object>> selectApprovalTrend(
      @Param("tenantId") String tenantId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("granularity") String granularity);
}
