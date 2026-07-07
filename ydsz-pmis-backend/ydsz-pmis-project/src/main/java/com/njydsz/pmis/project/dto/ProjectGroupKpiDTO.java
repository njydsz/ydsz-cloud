package com.njydsz.pmis.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目群 KPI 视图
 *
 * <p>按事业群/区域维度聚合的 KPI 子项，用于驾驶舱项目群对比。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectGroupKpiDTO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    /** 项目群编码 */
    private String groupCode;

    /** 项目群名称 */
    private String groupName;

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

    /** 毛利率（0-1） */
    private BigDecimal grossMargin;

    /** EVM 红色告警项目数 */
    private Integer evmRedCount;
}
