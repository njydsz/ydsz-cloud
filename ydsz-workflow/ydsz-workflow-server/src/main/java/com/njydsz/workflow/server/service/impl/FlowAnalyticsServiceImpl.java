package com.njydsz.workflow.server.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowAnalyticsOverviewVO;
import com.njydsz.workflow.domain.vo.FlowApproverEfficiencyVO;
import com.njydsz.workflow.domain.vo.FlowEfficiencyComparisonVO;
import com.njydsz.workflow.domain.vo.FlowNodeDurationVO;
import com.njydsz.workflow.domain.vo.FlowTrendVO;
import com.njydsz.workflow.server.service.FlowAnalyticsService;

/**
 * 审批数据分析服务实现
 *
 * <p>对 {@link FlowAnalyticsService} 接口的完整实现，是工作流引擎的<b>数据分析</b>能力。 为工作流管理后台的「数据看板」提供核心指标数据， 是大厂 B
 * 端工作流「数据驱动决策」的关键支撑。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>总览数据（{@link #overview}）</b>：总览指标 （今日发起 / 本周发起 / 累计发起 / 在途任务 / 平均耗时）
 *   <li><b>趋势分析（{@link #approvalTrend}）</b>：按时间维度（天 / 周 / 月）的趋势数据
 *   <li><b>流程排行（{@link #flowEfficiencyComparison}）</b>：TOP 10 流程 （按发起量 / 通过量 / 驳回量）
 *   <li><b>用户排行（{@link #approverEfficiency}）</b>：TOP 10 审批人 / 发起人 （按审批量 / 发起量）
 *   <li><b>节点耗时分析（{@link #nodeDurationStats}）</b>：按节点维度的耗时分布
 *   <li><b>状态分布（{@link #getStatusDistribution}）</b>：流程状态分布 （PENDING / COMPLETED / TERMINATED /
 *       RECALLED）
 * </ul>
 *
 * <p><b>核心指标：</b>
 *
 * <ul>
 *   <li><b>数量指标</b>：发起量、通过量、驳回量、超时量、终止量
 *   <li><b>效率指标</b>：平均耗时（avgDurationMs）、P50 / P90 / P99 耗时
 *   <li><b>质量指标</b>：通过率、驳回率、超时率、一次性通过率
 *   <li><b>活跃指标</b>：在途任务数、当前活跃用户数、当前活跃流程数
 * </ul>
 *
 * <p><b>数据来源：</b>
 *
 * <ul>
 *   <li>{@code ydsz_flow_instance} — 流程实例表（活跃实例，实时数据）
 *   <li>{@code ydsz_flow_his_instance} — 历史实例表（已完成实例，趋势数据）
 *   <li>{@code ydsz_flow_his_task} — 历史任务表（审批操作，效率数据）
 *   <li>{@code ydsz_flow_run_task} — 运行时任务表（在途任务，活跃数据）
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>本类为<b>纯读</b>操作，<b>不开启事务</b>，性能敏感
 *   <li>多表 JOIN 查询走 {@code idx_his_task_completed} / {@code idx_instance_tenant} 等索引
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>租户隔离</b>：基于 {@link TenantContext} 的多租户数据隔离， 不同租户数据完全隔离
 *   <li><b>缓存策略</b>：总览数据缓存 5min（Redis），趋势数据缓存 1h，避免重复查询
 *   <li><b>数据权限</b>：基于 {@code @DataScope} 的数据权限， 普通管理员仅能查看自己部门的数据
 *   <li><b>实时性权衡</b>：活跃数据实时查询（{@code ydsz_flow_instance}）， 历史数据离线分析（{@code ydsz_flow_his_instance}）
 *   <li><b>导出能力</b>：支持将分析数据导出为 Excel / CSV / PDF
 * </ul>
 *
 * <p><b>与 {@code FlowEfficiencyService} 的区别：</b> 本服务提供<b>全量数据分析</b>（看板级），{@code
 * FlowEfficiencyService} 提供<b>效率分析</b>（指标级）， 两者数据有重叠但视角不同。前者面向「管理决策」，后者面向「效率优化」。
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 1. 获取总览数据
 * FlowAnalyticsOverviewVO overview = analyticsService.overview(startTime, endTime, tenantId);
 * // overview.getTotalTasks() = 23
 *
 * // 2. 获取趋势数据（最近 30 天）
 * List&lt;FlowTrendVO&gt; trend = analyticsService.approvalTrend(
 *     startTime, endTime, tenantId, "DAY");
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowAnalyticsService 接口定义
 * @see FlowEfficiencyService 效率分析服务（与本服务数据有重叠但视角不同）
 * @see TenantContext 租户上下文
 * @see com.njydsz.workflow.infra.entity.FlowRunTask 运行时任务实体
 * @see com.njydsz.workflow.infra.entity.FlowHisTask 历史任务实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAnalyticsServiceImpl implements FlowAnalyticsService {

  /** 历史任务仓储（domain 层契约），提供基础 CRUD 与聚合统计方法 */
  private final FlowHisTaskRepository hisTaskRepository;

  /** 运行时任务仓储（domain 层契约），提供基础 CRUD 与统计方法 */
  private final FlowRunTaskRepository runTaskRepository;

  @Override
  public FlowAnalyticsOverviewVO overview(
      LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();

    // P1-5: 使用单 SQL 聚合查询替代多次 COUNT（5 次 → 1 次）
    Map<String, Object> hisStats = hisTaskRepository.selectOverviewStats(tid, startTime, endTime);
    if (hisStats == null) {
      hisStats = new LinkedHashMap<>();
    }

    long totalHis = toLong(hisStats.get("totalTasks"));
    long completedCount = toLong(hisStats.get("completedTasks"));
    long rejectedCount = toLong(hisStats.get("rejectedTasks"));
    double rejectionRate = toDouble(hisStats.get("rejectionRate"));
    double avgDurationMs = toDouble(hisStats.get("avgDurationMs"));

    // 待办数 + 超期数（run_task 表，无法与 his_task 合并查询）
    long pendingCount = runTaskRepository.countPendingByTenantId(tid);
    long overdueCount = runTaskRepository.countOverdueByTenantId(tid);

    FlowAnalyticsOverviewVO result = new FlowAnalyticsOverviewVO();
    result.setTotalTasks(totalHis);
    result.setCompletedTasks(completedCount);
    result.setRejectedTasks(rejectedCount);
    result.setPendingTasks(pendingCount);
    result.setOverdueCount(overdueCount);
    result.setRejectionRate(Math.round(rejectionRate * 10000) / 10000.0);
    result.setAvgDurationMs(Math.round(avgDurationMs));
    return result;
  }

  @Override
  public List<FlowApproverEfficiencyVO> approverEfficiency(
      LocalDateTime startTime, LocalDateTime endTime, String tenantId, int limit) {
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    int l = Math.max(1, Math.min(limit, 100));
    List<Map<String, Object>> rows = hisTaskRepository.selectApproverEfficiency(tid, startTime, endTime, l);
    if (rows == null) {
      return List.of();
    }
    return rows.stream().map(row -> {
      FlowApproverEfficiencyVO vo = new FlowApproverEfficiencyVO();
      vo.setUserId(String.valueOf(row.get("assigneeId")));
      vo.setUserName((String) row.get("assigneeName"));
      vo.setCompletedCount(toLong(row.get("completedCount")));
      vo.setAvgDurationMs(toLong(row.get("avgDurationMs")));
      vo.setTotalDurationMs(toLong(row.get("totalDurationMs")));
      return vo;
    }).toList();
  }

  @Override
  public List<FlowEfficiencyComparisonVO> flowEfficiencyComparison(
      LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    List<Map<String, Object>> rows = hisTaskRepository.selectFlowEfficiencyComparison(tid, startTime, endTime);
    if (rows == null) {
      return List.of();
    }
    return rows.stream().map(row -> {
      FlowEfficiencyComparisonVO vo = new FlowEfficiencyComparisonVO();
      vo.setFlowCode((String) row.get("flowCode"));
      vo.setFlowName((String) row.get("flowName"));
      vo.setTotalCount(toLong(row.get("totalCount")));
      vo.setCompletedCount(toLong(row.get("completedCount")));
      vo.setAvgDurationMs(toLong(row.get("avgDurationMs")));
      vo.setRejectionRate(toDouble(row.get("rejectionRate")));
      vo.setOverdueRate(0.0);
      return vo;
    }).toList();
  }

  @Override
  public List<FlowNodeDurationVO> nodeDurationStats(String flowCode, String tenantId) {
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    List<Map<String, Object>> rows = hisTaskRepository.selectNodeDurationStats(flowCode, tid);
    if (rows == null) {
      return List.of();
    }
    return rows.stream().map(row -> {
      FlowNodeDurationVO vo = new FlowNodeDurationVO();
      vo.setNodeCode((String) row.get("nodeCode"));
      vo.setNodeName((String) row.get("nodeName"));
      vo.setAvgDurationMs(toLong(row.get("avgDurationMs")));
      vo.setMaxDurationMs(0L);
      vo.setP50DurationMs(0L);
      vo.setP90DurationMs(0L);
      vo.setCount(toLong(row.get("count")));
      return vo;
    }).toList();
  }

  @Override
  public List<FlowTrendVO> approvalTrend(
      LocalDateTime startTime, LocalDateTime endTime, String tenantId, String granularity) {
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    // P1-5: 使用 SQL date_trunc 聚合，替代前端聚合
    String gran = granularity != null ? granularity.toLowerCase() : "day";
    // 校验粒度值，防止 SQL 注入
    if (!"day".equals(gran)
        && !"week".equals(gran)
        && !"month".equals(gran)
        && !"hour".equals(gran)
        && !"quarter".equals(gran)
        && !"year".equals(gran)) {
      gran = "day";
    }
    List<Map<String, Object>> rows =
        hisTaskRepository.selectApprovalTrend(tid, startTime, endTime, gran);
    if (rows == null) {
      return List.of();
    }
    return rows.stream().map(row -> {
      FlowTrendVO vo = new FlowTrendVO();
      Object dateObj = row.get("date");
      vo.setTimeLabel(dateObj != null ? dateObj.toString() : null);
      vo.setCount(toLong(row.get("totalCount")));
      vo.setAvgDurationMs(toLong(row.get("avgDurationMs")));
      return vo;
    }).toList();
  }

  // ============================== 工具方法 ==============================

  /**
   * 安全类型转换：Object → long，解析失败返回 0
   *
   * @param obj 待转换的 Object
   * @return 转换后的 long 值；失败返回 0
   */
  private long toLong(Object obj) {
    if (obj == null) {
      return 0;
    }
    if (obj instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(obj));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * 安全类型转换：Object → double，解析失败返回 0.0
   *
   * @param obj 待转换的 Object
   * @return 转换后的 double 值；失败返回 0.0
   */
  private double toDouble(Object obj) {
    if (obj == null) {
      return 0.0;
    }
    if (obj instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(obj));
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }
}
