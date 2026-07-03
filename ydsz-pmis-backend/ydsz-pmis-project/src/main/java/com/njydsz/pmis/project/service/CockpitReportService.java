package com.njydsz.pmis.project.service;

import com.njydsz.pmis.project.dto.CockpitAlertSummaryVO;
import com.njydsz.pmis.project.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.project.dto.CockpitKpiVO;
import com.njydsz.pmis.project.dto.ExecutiveOverviewVO;
import com.njydsz.pmis.project.dto.KpiTrendVO;
import com.njydsz.pmis.project.dto.ProjectGroupKpiDTO;

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
     * @return 总览 KPI 数据
     */
    CockpitKpiVO overview(String period, CockpitDrillDownDTO drillDown);

    /**
     * EVM 健康分布
     *
     * @param period    期间（YYYY-MM，空 = 累计）
     * @param drillDown 下钻维度（可空）
     * @return EVM 健康分布（RED/YELLOW/GREEN → 数量）
     */
    Map<String, Integer> evmHealthDistribution(String period, CockpitDrillDownDTO drillDown);

    /**
     * Bench 闲置成本聚合
     *
     * @param drillDown 下钻维度（可空）
     * @return Bench 闲置成本汇总数据
     */
    Map<String, Object> benchCostSummary(CockpitDrillDownDTO drillDown);

    /**
     * 可计费利用率均值
     *
     * @param drillDown 下钻维度（可空）
     * @return 利用率汇总数据
     */
    Map<String, Object> utilizationSummary(CockpitDrillDownDTO drillDown);

    /**
     * 按事业部下钻
     *
     * @param period 期间（YYYY-MM，空 = 累计）
     * @return 事业部维度 KPI 列表
     */
    List<Map<String, Object>> drillByDept(String period);

    /**
     * 按项目类型下钻
     *
     * @param period 期间（YYYY-MM，空 = 累计）
     * @return 项目类型维度 KPI 列表
     */
    List<Map<String, Object>> drillByProjectType(String period);

    /**
     * 按客户下钻
     *
     * @param period 期间（YYYY-MM，空 = 累计）
     * @return 客户维度 KPI 列表
     */
    List<Map<String, Object>> drillByCustomer(String period);

    /**
     * 合同总额年度趋势（P2-4 体验增强）
     *
     * <p>基于 invoice 表 ISSUED 状态的合同开票金额，按 invoice_date 的年份聚合。
     * 返回字段包含：years / amountSeries / projectCountSeries / summary（峰值年份、同比增长率、累计）。
     *
     * @return 年度趋势数据
     */
    Map<String, Object> contractAmountYearlyTrend();

    // ========== 批次18 增量 ==========

    /**
     * 预警事件摘要：触发规则列表 + 严重度计数 + 顶部事件
     *
     * @param period    期间（YYYY-MM，空 = 累计）
     * @param drillDown 下钻维度（可空）
     * @return 预警事件摘要
     */
    CockpitAlertSummaryVO alertSummary(String period, CockpitDrillDownDTO drillDown);

    /**
     * 项目群 KPI 列表（按事业群/区域聚合）
     *
     * @param period    期间（YYYY-MM，空 = 累计）
     * @param drillDown 下钻维度（可空）
     * @return 项目群 KPI 列表
     */
    List<ProjectGroupKpiDTO> projectGroupOverview(String period, CockpitDrillDownDTO drillDown);

    /**
     * 高管看板：核心 KPI + 项目群对比 + 健康度评分
     *
     * @param period    期间（YYYY-MM，空 = 累计）
     * @param drillDown 下钻维度（可空）
     * @return 高管看板数据
     */
    ExecutiveOverviewVO executiveOverview(String period, CockpitDrillDownDTO drillDown);

    /**
     * KPI 趋势：最近 N 个月核心 KPI 序列
     *
     * @param months 月数（空 = 默认 6 个月）
     * @return KPI 趋势数据
     */
    KpiTrendVO kpiTrend(Integer months);
}
