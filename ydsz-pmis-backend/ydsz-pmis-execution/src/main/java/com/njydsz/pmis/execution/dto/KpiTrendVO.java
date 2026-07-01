package com.njydsz.pmis.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * KPI 趋势视图
 *
 * <p>按月份返回最近 N 个月的 6 项核心 KPI 序列，用于驾驶舱趋势小图。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 月份列表（yyyy-MM） */
    private List<String> periods;

    /** 合同总额序列 */
    private List<BigDecimal> contractAmountSeries;
    /** 已确认收入序列 */
    private List<BigDecimal> confirmedRevenueSeries;
    /** 累计成本序列 */
    private List<BigDecimal> totalCostSeries;
    /** 毛利序列 */
    private List<BigDecimal> grossProfitSeries;
    /** 毛利率序列（百分比 0-100） */
    private List<BigDecimal> grossMarginPctSeries;
    /** 在执行项目数序列 */
    private List<Integer> activeProjectsSeries;

    /** 摘要：最新月份 vs 上月 增长率（合同 / 收入 / 毛利） */
    private BigDecimal contractMtdGrowth;
    private BigDecimal revenueMtdGrowth;
    private BigDecimal profitMtdGrowth;
}
