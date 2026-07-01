package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
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
 * 经营驾驶舱 Controller
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

    @Operation(summary = "合同总额年度趋势（P2-4）")
    @GetMapping("/contract-yearly-trend")
    public R<Map<String, Object>> contractYearlyTrend() {
        return R.ok(service.contractAmountYearlyTrend());
    }
}
