package com.njydsz.pmis.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 高管看板聚合视图
 *
 * <p>面向公司高管（CEO/CFO/COO）的核心 KPI 摘要 + 项目群对比 + 健康度分布。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveOverviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 顶部 KPI ==========
    /** 在执行项目数 */
    private Integer activeProjects;
    /** 合同总额 */
    private BigDecimal totalContractAmount;
    /** 已确认收入 */
    private BigDecimal confirmedRevenue;
    /** 累计成本 */
    private BigDecimal totalCost;
    /** 累计毛利 */
    private BigDecimal grossProfit;
    /** 平均毛利率（0-1） */
    private BigDecimal grossMargin;
    /** 可计费利用率均值（0-1） */
    private BigDecimal avgBillableUtilization;
    /** Bench 累计闲置成本 */
    private BigDecimal benchIdleCost;

    // ========== 健康度 ==========
    /** EVM 红色项目数 */
    private Integer evmRedCount;
    /** EVM 黄色项目数 */
    private Integer evmYellowCount;
    /** EVM 绿色项目数 */
    private Integer evmGreenCount;
    /** 健康项目占比（绿色 / 全部） */
    private BigDecimal healthRatio;
    /** 风险项目数（riskLevel=RED/YELLOW） */
    private Integer riskProjectCount;
    /** 风险项目占比 */
    private BigDecimal riskProjectRatio;

    // ========== 项目群对比 ==========
    private List<ProjectGroupKpiDTO> projectGroups;

    // ========== 综合评分（0-100） ==========
    /** 综合健康度评分（基于健康占比 + 毛利率 + 利用率加权） */
    private BigDecimal healthScore;
    /** 评分等级：A/B/C/D */
    private String healthGrade;
}
