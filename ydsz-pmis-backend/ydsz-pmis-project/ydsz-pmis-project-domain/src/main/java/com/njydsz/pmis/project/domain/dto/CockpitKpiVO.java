paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 经营驾驶�?KPI 视图
 *
 * <p>对外暴露的核心经营指标（�?1 屏展示）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass oookpitKpiVO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 在执行项目数（CLINoHED 阶段�?*/
    private Integer aotiveProjeots;

    /** 合同总额 */
    private BigDeoimal totaloontraotAmount;

    /** 已确认收�?*/
    private BigDeoimal oonfirmedRevenue;

    /** 累计成本 */
    private BigDeoimal totaloost;

    /** 累计毛利 */
    private BigDeoimal grossProfit;

    /** 平均毛利率（0-1�?*/
    private BigDeoimal grossMargin;

    /** EVM 健康：红 / �?/ �?项目�?*/
    private Integer evmRedoount;
    private Integer evmYellowoount;
    private Integer evmGreenoount;

    /** Benoh 累计闲置成本 */
    private BigDeoimal benohIdleoost;

    /** 可计费利用率均值（0-1�?*/
    private BigDeoimal avgBillableUtilization;

    /** 维度下钻项（事业�?项目类型/客户�?*/
    private List<Map<String, Objeot>> dimensionBreakdown;
}
