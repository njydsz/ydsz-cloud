package com.njydsz.workflow.server.service;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.workflow.domain.vo.FlowAnalyticsOverviewVO;
import com.njydsz.workflow.domain.vo.FlowApproverEfficiencyVO;
import com.njydsz.workflow.domain.vo.FlowEfficiencyComparisonVO;
import com.njydsz.workflow.domain.vo.FlowNodeDurationVO;
import com.njydsz.workflow.domain.vo.FlowTrendVO;

/**
 * 流程分析服务。
 *
 * <p>多维度统计流程实例、任务、SLA 数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowAnalyticsService {

  /**
   * 审批总览仪表盘
   *
   * <p>汇总指定时间范围内的核心指标：
   *
   * <ul>
   *   <li>totalTasks — 任务总数
   *   <li>completedTasks — 通过数
   *   <li>rejectedTasks — 驳回数
   *   <li>pendingTasks — 待办数
   *   <li>avgDurationMs — 平均处理耗时
   *   <li>rejectionRate — 驳回率
   *   <li>overdueCount — 超期数
   * </ul>
   *
   * @param startTime 起始时间（可空）
   * @param endTime 截止时间（可空）
   * @param tenantId 租户 ID（可空）
   * @return 指标 VO
   */
  FlowAnalyticsOverviewVO overview(LocalDateTime startTime, LocalDateTime endTime, String tenantId);

  /**
   * 办理人效率排行
   *
   * @param startTime 起始时间
   * @param endTime 截止时间
   * @param tenantId 租户 ID
   * @param limit 返回条数（默认 20）
   * @return 办理人效率列表
   */
  List<FlowApproverEfficiencyVO> approverEfficiency(
      LocalDateTime startTime, LocalDateTime endTime, String tenantId, int limit);

  /**
   * 流程效率对比
   *
   * @param startTime 起始时间
   * @param endTime 截止时间
   * @param tenantId 租户 ID
   * @return 流程效率列表
   */
  List<FlowEfficiencyComparisonVO> flowEfficiencyComparison(
      LocalDateTime startTime, LocalDateTime endTime, String tenantId);

  /**
   * 节点耗时分析
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID
   * @return 节点耗时统计列表
   */
  List<FlowNodeDurationVO> nodeDurationStats(String flowCode, String tenantId);

  /**
   * 审批趋势分析（按天/周/月聚合）
   *
   * @param startTime 起始时间
   * @param endTime 截止时间
   * @param tenantId 租户 ID
   * @param granularity 粒度：DAY / WEEK / MONTH
   * @return 趋势数据列表
   */
  List<FlowTrendVO> approvalTrend(
      LocalDateTime startTime, LocalDateTime endTime, String tenantId, String granularity);
}
