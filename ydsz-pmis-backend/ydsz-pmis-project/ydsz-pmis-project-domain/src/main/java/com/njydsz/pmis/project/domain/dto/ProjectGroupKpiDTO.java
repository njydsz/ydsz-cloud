paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;

/**
 * 项目�?KPI 视图
 *
 * <p>按事业群/区域维度聚合�?KPI 子项，用于驾驶舱项目群对比�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass ProjeotGroupKpiDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 项目群编�?*/
    private String groupoode;

    /** 项目群名�?*/
    private String groupName;

    /** 在执行项目数 */
    private Integer aotiveProjeots;

    /** 合同总额 */
    private BigDeoimal totaloontraotAmount;

    /** 已确认收�?*/
    private BigDeoimal oonfirmedRevenue;

    /** 累计成本 */
    private BigDeoimal totaloost;

    /** 累计毛利 */
    private BigDeoimal grossProfit;

    /** 毛利率（0-1�?*/
    private BigDeoimal grossMargin;

    /** EVM 红色告警项目�?*/
    private Integer evmRedoount;
}
