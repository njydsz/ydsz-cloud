package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.service.AdvancedReportService;
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
 * 高级报表 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "高级报表")
@RestController
@RequestMapping("/api/v1/execution/advanced-report")
@RequiredArgsConstructor
public class AdvancedReportController {

    private final AdvancedReportService service;

    @Operation(summary = "EVM 挣值管理报表")
    @GetMapping("/evm")
    public R<List<Map<String, Object>>> evm(@RequestParam Long initiationId) {
        return R.ok(service.evmReport(initiationId));
    }

    @Operation(summary = "人效排行榜")
    @GetMapping("/utilization-rank")
    public R<List<Map<String, Object>>> utilizationRank(@RequestParam(defaultValue = "20") int top) {
        return R.ok(service.utilizationRank(top));
    }

    @Operation(summary = "Bench 闲置成本报表")
    @GetMapping("/bench-cost")
    public R<List<Map<String, Object>>> benchCost() {
        return R.ok(service.benchCostReport());
    }

    @Operation(summary = "双费率利润对比表")
    @GetMapping("/dual-rate")
    public R<List<Map<String, Object>>> dualRate(@RequestParam(required = false) String period) {
        return R.ok(service.dualRateProfitCompare(period));
    }

    @Operation(summary = "资源负载甘特图")
    @GetMapping("/gantt")
    public R<List<Map<String, Object>>> gantt(@RequestParam Long initiationId) {
        return R.ok(service.resourceGantt(initiationId));
    }

    @Operation(summary = "项目风险预警看板")
    @GetMapping("/risk-dashboard")
    public R<List<Map<String, Object>>> riskDashboard() {
        return R.ok(service.riskDashboard());
    }
}
