package com.njydsz.pmis.project.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 经营驾驶舱 KPI 视图
 *
 * <p>对外暴露的核心经营指标（卡 1 屏展示）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class CockpitKpiVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 在执行项目数（CLINCHED 阶段） */
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

    /** EVM 健康：红 / 黄 / 绿 项目数 */
    private Integer evmRedCount;
    private Integer evmYellowCount;
    private Integer evmGreenCount;

    /** Bench 累计闲置成本 */
    private BigDecimal benchIdleCost;

    /** 可计费利用率均值（0-1） */
    private BigDecimal avgBillableUtilization;

    /** 维度下钻项（事业部/项目类型/客户） */
    private List<Map<String, Object>> dimensionBreakdown;
}
