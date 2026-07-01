package com.njydsz.pmis.execution.service;

import com.njydsz.pmis.execution.dto.CockpitAlertSummaryVO;
import com.njydsz.pmis.execution.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
import com.njydsz.pmis.execution.dto.ExecutiveOverviewVO;
import com.njydsz.pmis.execution.dto.KpiTrendVO;
import com.njydsz.pmis.execution.dto.ProjectGroupKpiDTO;

import java.util.List;
import java.util.Map;

/**
 * 经营驾驶舱服务（批次18 增强）
 *
 * <p>提供 6 类经营 KPI + 3 维度下钻 + 预警事件 + 项目群对比 + 高管看板 + KPI 趋势。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface CockpitReportService {

    /**
     * 驾驶舱总览 KPI
     *
     * @param period   期间（YYYY-MM，空 = 累计）
     * @param drillDown 下钻维度（可空）
     */
    CockpitKpiVO overview(String period, CockpitDrillDownDTO drillDown);

    /**
     * EVM 健康分布
     */
    Map<String, Integer> evmHealthDistribution(String period, CockpitDrillDownDTO drillDown);

    /**
     * Bench 闲置成本聚合
     */
    Map<String, Object> benchCostSummary(CockpitDrillDownDTO drillDown);

    /**
     * 可计费利用率均值
     */
    Map<String, Object> utilizationSummary(CockpitDrillDownDTO drillDown);

    /**
     * 按事业部下钻
     */
    List<Map<String, Object>> drillByDept(String period);

    /**
     * 按项目类型下钻
     */
    List<Map<String, Object>> drillByProjectType(String period);

    /**
     * 按客户下钻
     */
    List<Map<String, Object>> drillByCustomer(String period);

    /**
     * 合同总额年度趋势（P2-4 体验增强）
     *
     * <p>基于 invoice 表 ISSUED 状态的合同开票金额，按 invoice_date 的年份聚合。
     * 返回字段包含：years / amountSeries / projectCountSeries / summary（峰值年份、同比增长率、累计）。
     */
    Map<String, Object> contractAmountYearlyTrend();

    // ========== 批次18 增量 ==========

    /**
     * 预警事件摘要：触发规则列表 + 严重度计数 + 顶部事件
     */
    CockpitAlertSummaryVO alertSummary(String period, CockpitDrillDownDTO drillDown);

    /**
     * 项目群 KPI 列表（按事业群/区域聚合）
     */
    List<ProjectGroupKpiDTO> projectGroupOverview(String period, CockpitDrillDownDTO drillDown);

    /**
     * 高管看板：核心 KPI + 项目群对比 + 健康度评分
     */
    ExecutiveOverviewVO executiveOverview(String period, CockpitDrillDownDTO drillDown);

    /**
     * KPI 趋势：最近 N 个月核心 KPI 序列
     */
    KpiTrendVO kpiTrend(Integer months);
}
