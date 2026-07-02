package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
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
    @PrePermission("cockpit:overview:view")
    @GetMapping("/overview")
    public Result<CockpitKpiVO> overview(@RequestParam(required = false) String period,
                                     CockpitDrillDownDTO drillDown) {
        return Result.ok(service.overview(period, drillDown));
    }

    @Operation(summary = "EVM 健康分布")
    @PrePermission("cockpit:overview:view")
    @GetMapping("/evm-health")
    public Result<Map<String, Integer>> evmHealth(@RequestParam(required = false) String period,
                                             CockpitDrillDownDTO drillDown) {
        return Result.ok(service.evmHealthDistribution(period, drillDown));
    }

    @Operation(summary = "Bench 闲置成本汇总")
    @PrePermission("cockpit:overview:view")
    @GetMapping("/bench-cost")
    public Result<Map<String, Object>> benchCost(CockpitDrillDownDTO drillDown) {
        return Result.ok(service.benchCostSummary(drillDown));
    }

    @Operation(summary = "可计费利用率汇总")
    @PrePermission("cockpit:overview:view")
    @GetMapping("/utilization")
    public Result<Map<String, Object>> utilization(CockpitDrillDownDTO drillDown) {
        return Result.ok(service.utilizationSummary(drillDown));
    }

    @Operation(summary = "按事业部下钻")
    @PrePermission("cockpit:drilldown:view")
    @GetMapping("/drill/dept")
    public Result<List<Map<String, Object>>> drillDept(@RequestParam(required = false) String period) {
        return Result.ok(service.drillByDept(period));
    }

    @Operation(summary = "按项目类型下钻")
    @PrePermission("cockpit:drilldown:view")
    @GetMapping("/drill/project-type")
    public Result<List<Map<String, Object>>> drillProjectType(@RequestParam(required = false) String period) {
        return Result.ok(service.drillByProjectType(period));
    }

    @Operation(summary = "按客户下钻")
    @PrePermission("cockpit:drilldown:view")
    @GetMapping("/drill/customer")
    public Result<List<Map<String, Object>>> drillCustomer(@RequestParam(required = false) String period) {
        return Result.ok(service.drillByCustomer(period));
    }

    @Operation(summary = "合同总额年度趋势")
    @PrePermission("cockpit:overview:view")
    @GetMapping("/contract-yearly-trend")
    public Result<Map<String, Object>> contractYearlyTrend() {
        return Result.ok(service.contractAmountYearlyTrend());
    }

    // ========== 批次18 增量端点 ==========

    @Operation(summary = "预警事件摘要（批次18）")
    @PrePermission("cockpit:alert:view")
    @GetMapping("/alerts")
    public Result<CockpitAlertSummaryVO> alerts(@RequestParam(required = false) String period,
                                            CockpitDrillDownDTO drillDown) {
        return Result.ok(service.alertSummary(period, drillDown));
    }

    @Operation(summary = "项目群驾驶舱（批次18）")
    @PrePermission("cockpit:overview:view")
    @GetMapping("/project-group")
    public Result<List<ProjectGroupKpiDTO>> projectGroup(@RequestParam(required = false) String period,
                                                      CockpitDrillDownDTO drillDown) {
        return Result.ok(service.projectGroupOverview(period, drillDown));
    }

    @Operation(summary = "高管看板（批次18）")
    @PrePermission("cockpit:overview:view")
    @GetMapping("/executive")
    public Result<ExecutiveOverviewVO> executive(@RequestParam(required = false) String period,
                                             CockpitDrillDownDTO drillDown) {
        return Result.ok(service.executiveOverview(period, drillDown));
    }

    @Operation(summary = "KPI 趋势（最近 N 个月，批次18）")
    @PrePermission("cockpit:overview:view")
    @GetMapping("/kpi-trend")
    public Result<KpiTrendVO> kpiTrend(@RequestParam(required = false, defaultValue = "12") Integer months) {
        return Result.ok(service.kpiTrend(months));
    }
}
