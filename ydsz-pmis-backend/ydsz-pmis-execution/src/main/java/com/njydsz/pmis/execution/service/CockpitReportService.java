package com.njydsz.pmis.execution.service;

import com.njydsz.pmis.execution.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;

import java.util.List;
import java.util.Map;

/**
 * 经营驾驶舱服务
 *
 * <p>提供 6 类经营 KPI + 3 维度下钻。
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
}
