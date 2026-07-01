package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.CockpitAlertSummaryVO;
import com.njydsz.pmis.execution.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
import com.njydsz.pmis.execution.dto.ExecutiveOverviewVO;
import com.njydsz.pmis.execution.dto.KpiTrendVO;
import com.njydsz.pmis.execution.dto.ProjectGroupKpiDTO;
import com.njydsz.pmis.execution.service.CockpitReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 经营驾驶舱 Controller（批次18 增强）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "经营驾驶舱")
@RestController
@RequestMapping("/api/v1/execution/cockpit")
@RequiredArgsConstructor
public class CockpitReportController {

    private final CockpitReportService service;

    @Operation(summary = "驾驶舱总览 KPI")
    @GetMapping("/overview")
    public R<CockpitKpiVO> overview(@RequestParam(required = false) String period,
                                     CockpitDrillDownDTO drillDown) {
        return R.ok(service.overview(period, drillDown));
    }

    @Operation(summary = "EVM 健康分布")
    @GetMapping("/evm-health")
    public R<Map<String, Integer>> evmHealth(@RequestParam(required = false) String period,
                                             CockpitDrillDownDTO drillDown) {
        return R.ok(service.evmHealthDistribution(period, drillDown));
    }

    @Operation(summary = "Bench 闲置成本汇总")
    @GetMapping("/bench-cost")
    public R<Map<String, Object>> benchCost(CockpitDrillDownDTO drillDown) {
        return R.ok(service.benchCostSummary(drillDown));
    }

    @Operation(summary = "可计费利用率汇总")
    @GetMapping("/utilization")
    public R<Map<String, Object>> utilization(CockpitDrillDownDTO drillDown) {
        return R.ok(service.utilizationSummary(drillDown));
    }

    @Operation(summary = "按事业部下钻")
    @GetMapping("/drill/dept")
    public R<List<Map<String, Object>>> drillDept(@RequestParam(required = false) String period) {
        return R.ok(service.drillByDept(period));
    }

    @Operation(summary = "按项目类型下钻")
    @GetMapping("/drill/project-type")
    public R<List<Map<String, Object>>> drillProjectType(@RequestParam(required = false) String period) {
        return R.ok(service.drillByProjectType(period));
    }

    @Operation(summary = "按客户下钻")
    @GetMapping("/drill/customer")
    public R<List<Map<String, Object>>> drillCustomer(@RequestParam(required = false) String period) {
        return R.ok(service.drillByCustomer(period));
    }

    @Operation(summary = "合同总额年度趋势")
    @GetMapping("/contract-yearly-trend")
    public R<Map<String, Object>> contractYearlyTrend() {
        return R.ok(service.contractAmountYearlyTrend());
    }

    // ========== 批次18 增量端点 ==========

    @Operation(summary = "预警事件摘要（批次18）")
    @GetMapping("/alerts")
    public R<CockpitAlertSummaryVO> alerts(@RequestParam(required = false) String period,
                                            CockpitDrillDownDTO drillDown) {
        return R.ok(service.alertSummary(period, drillDown));
    }

    @Operation(summary = "项目群驾驶舱（批次18）")
    @GetMapping("/project-group")
    public R<List<ProjectGroupKpiDTO>> projectGroup(@RequestParam(required = false) String period,
                                                      CockpitDrillDownDTO drillDown) {
        return R.ok(service.projectGroupOverview(period, drillDown));
    }

    @Operation(summary = "高管看板（批次18）")
    @GetMapping("/executive")
    public R<ExecutiveOverviewVO> executive(@RequestParam(required = false) String period,
                                             CockpitDrillDownDTO drillDown) {
        return R.ok(service.executiveOverview(period, drillDown));
    }

    @Operation(summary = "KPI 趋势（最近 N 个月，批次18）")
    @GetMapping("/kpi-trend")
    public R<KpiTrendVO> kpiTrend(@RequestParam(required = false, defaultValue = "12") Integer months) {
        return R.ok(service.kpiTrend(months));
    }
}
