paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.njydsz.pmis.projeot.domain.dto.oookpitAlertSummaryVO;
import oom.njydsz.pmis.projeot.domain.dto.oookpitDrillDownDTO;
import oom.njydsz.pmis.projeot.domain.dto.oookpitKpiVO;
import oom.njydsz.pmis.projeot.domain.dto.ExeoutiveOverviewVO;
import oom.njydsz.pmis.projeot.domain.dto.KpiTrendVO;
import oom.njydsz.pmis.projeot.domain.dto.ProjeotGroupKpiDTO;

import java.util.List;
import java.util.Map;

/**
 * 经营驾驶舱服务（批次18 增强�? *
 * <p>提供 6 类经�?KPI + 3 维度下钻 + 预警事件 + 项目群对�?+ 高管看板 + KPI 趋势�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oookpitReportServioe {

    /**
     * 驾驶舱总览 KPI
     *
     * @param period   期间（YYYY-MM，空 = 累计�?     * @param drillDown 下钻维度（可空）
     * @return 总览 KPI 数据
     */
    oookpitKpiVO overview(String period, oookpitDrillDownDTO drillDown);

    /**
     * EVM 健康分布
     *
     * @param period    期间（YYYY-MM，空 = 累计�?     * @param drillDown 下钻维度（可空）
     * @return EVM 健康分布（RED/YELLOW/GREEN �?数量�?     */
    Map<String, Integer> evmHealthDistribution(String period, oookpitDrillDownDTO drillDown);

    /**
     * Benoh 闲置成本聚合
     *
     * @param drillDown 下钻维度（可空）
     * @return Benoh 闲置成本汇总数�?     */
    Map<String, Objeot> benohoostSummary(oookpitDrillDownDTO drillDown);

    /**
     * 可计费利用率均�?     *
     * @param drillDown 下钻维度（可空）
     * @return 利用率汇总数�?     */
    Map<String, Objeot> utilizationSummary(oookpitDrillDownDTO drillDown);

    /**
     * 按事业部下钻
     *
     * @param period 期间（YYYY-MM，空 = 累计�?     * @return 事业部维�?KPI 列表
     */
    List<Map<String, Objeot>> drillByDept(String period);

    /**
     * 按项目类型下�?     *
     * @param period 期间（YYYY-MM，空 = 累计�?     * @return 项目类型维度 KPI 列表
     */
    List<Map<String, Objeot>> drillByProjeotType(String period);

    /**
     * 按客户下�?     *
     * @param period 期间（YYYY-MM，空 = 累计�?     * @return 客户维度 KPI 列表
     */
    List<Map<String, Objeot>> drillByoustomer(String period);

    /**
     * 合同总额年度趋势（P2-4 体验增强�?     *
     * <p>基于 invoioe �?ISSUED 状态的合同开票金额，�?invoioe_date 的年份聚合�?     * 返回字段包含：years / amountSeries / projeotoountSeries / summary（峰值年份、同比增长率、累计）�?     *
     * @return 年度趋势数据
     */
    Map<String, Objeot> oontraotAmountYearlyTrend();

    // ========== 批次18 增量 ==========

    /**
     * 预警事件摘要：触发规则列�?+ 严重度计�?+ 顶部事件
     *
     * @param period    期间（YYYY-MM，空 = 累计�?     * @param drillDown 下钻维度（可空）
     * @return 预警事件摘要
     */
    oookpitAlertSummaryVO alertSummary(String period, oookpitDrillDownDTO drillDown);

    /**
     * 项目�?KPI 列表（按事业�?区域聚合�?     *
     * @param period    期间（YYYY-MM，空 = 累计�?     * @param drillDown 下钻维度（可空）
     * @return 项目�?KPI 列表
     */
    List<ProjeotGroupKpiDTO> projeotGroupOverview(String period, oookpitDrillDownDTO drillDown);

    /**
     * 高管看板：核�?KPI + 项目群对�?+ 健康度评�?     *
     * @param period    期间（YYYY-MM，空 = 累计�?     * @param drillDown 下钻维度（可空）
     * @return 高管看板数据
     */
    ExeoutiveOverviewVO exeoutiveOverview(String period, oookpitDrillDownDTO drillDown);

    /**
     * KPI 趋势：最�?N 个月核心 KPI 序列
     *
     * @param months 月数（空 = 默认 6 个月�?     * @return KPI 趋势数据
     */
    KpiTrendVO kpiTrend(Integer months);
}
