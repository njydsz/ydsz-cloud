package com.njydsz.workflow.server.service;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.workflow.domain.vo.FlowAnalyticsOverviewVO;
import com.njydsz.workflow.domain.vo.FlowAnomalyVO;
import com.njydsz.workflow.domain.vo.FlowApproverEfficiencyVO;
import com.njydsz.workflow.domain.vo.FlowBottleneckVO;
import com.njydsz.workflow.domain.vo.FlowEfficiencyComparisonVO;
import com.njydsz.workflow.domain.vo.FlowMigrationImpactVO;
import com.njydsz.workflow.domain.vo.FlowNodeDurationVO;
import com.njydsz.workflow.domain.vo.FlowTrendVO;

/**
 * 流程分析服务。
 *
 * <p>多维度统计流程实例、任务、SLA 数据。
 *
 * @author ydsz-team
 * @since 26.09.01
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

  // ==================== F-05 瓶颈热力图 ====================

  /**
   * 瓶颈热力图（按流程定义 × 节点维度统计平均耗时与超时率）。
   *
   * <p>输出每个流程定义下各节点的评级指标，用于识别瓶颈节点和超时重灾区。
   *
   * @param startTime 起始时间（可空，默认近 30 天）
   * @param endTime 截止时间（可空）
   * @param tenantId 租户 ID
   * @return 瓶颈节点列表（按 avgDurationMs 降序）
   */
  List<FlowBottleneckVO> bottleneckHeatmap(
      LocalDateTime startTime, LocalDateTime endTime, String tenantId);

  // ==================== F-06 异常告警检测 ====================

  /**
   * 异常告警检测（驳回率突增/耗时突增/异常积压/超期卡住）。
   *
   * <p>基于阈值规则检测异常，命中 {@code warnLevel=YELLOW} 时触发站内信提醒，
   * 命中 {@code warnLevel=RED} 时同步触发短信/邮件告警（对接 ydzs-message 引擎）。
   *
   * @param tenantId 租户 ID
   * @param stuckThresholdHours 卡住阈值（小时），默认 48
   * @param rejectRateThreshold 驳回率阈值（0~1），默认 0.5
   * @param backlogThreshold 异常积压阈值（同时超期实例数），默认 20
   * @return 异常告警列表（按 warnLevel 严重程度降序）
   */
  List<FlowAnomalyVO> detectAnomalies(
      String tenantId,
      Integer stuckThresholdHours,
      Double rejectRateThreshold,
      Integer backlogThreshold);

  // ==================== F-04 变更影响预览 ====================

  /**
   * 变更影响预览（发布前 dry-run）。
   *
   * <p>分析流程定义发布（或节点配置变更）对在途实例的影响范围：
   * 是否有活跃实例卡在将被删除/改动的节点上。
   *
   * @param definitionId 流程定义 ID
   * @param nodeIdToCheck 待校验的节点 ID（可空，为空则全量扫描）
   * @param tenantId 租户 ID
   * @return 影响分析结果（受影响的在途实例 + 风险评估等级）
   */
  FlowMigrationImpactVO previewImpact(
      String definitionId, String nodeIdToCheck, String tenantId);
}
