package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.service.AdvancedReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

    @Operation(summary = "人效排行榜（默认近 3 个月）")
    @GetMapping("/utilization-rank")
    public R<List<Map<String, Object>>> utilizationRank(
            @RequestParam(defaultValue = "20") int top) {
        return R.ok(service.utilizationRank(top));
    }

    @Operation(summary = "人效排行榜（自定义时间窗口/事业部）")
    @GetMapping("/utilization-rank/range")
    public R<List<Map<String, Object>>> utilizationRankRange(
            @RequestParam(defaultValue = "20") int top,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String department) {
        return R.ok(service.utilizationRank(top, from, to, department));
    }

    @Operation(summary = "单员工可计费利用率")
    @GetMapping("/utilization/employee")
    public R<Map<String, Object>> utilizationOf(
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.utilizationOf(employeeId, from, to));
    }

    @Operation(summary = "事业部级可计费利用率")
    @GetMapping("/utilization/department")
    public R<List<Map<String, Object>>> utilizationByDepartment(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.utilizationByDepartment(from, to));
    }

    @Operation(summary = "Bench 闲置成本报表（默认近 30 天）")
    @GetMapping("/bench-cost")
    public R<List<Map<String, Object>>> benchCost() {
        return R.ok(service.benchCostReport());
    }

    @Operation(summary = "Bench 闲置成本报表（自定义时间窗口）")
    @GetMapping("/bench-cost/range")
    public R<List<Map<String, Object>>> benchCostRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.benchCostReport(from, to));
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

    @Operation(summary = "项目风险矩阵热力图（P2-2）")
    @GetMapping("/risk-matrix")
    public R<Map<String, Object>> riskMatrix(
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) String status) {
        return R.ok(service.riskMatrix(initiationId, riskType, status));
    }

    @Operation(summary = "资源占用趋势图 双 Y 轴（P2-3）")
    @GetMapping("/utilization-trend")
    public R<Map<String, Object>> utilizationTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String department) {
        return R.ok(service.resourceUtilizationTrend(from, to, department));
    }

    @Operation(summary = "项目健康仪表盘 CPI/SPI/毛利率（P2-5）")
    @GetMapping("/project-health-dashboard")
    public R<Map<String, Object>> projectHealthDashboard(
            @RequestParam(required = false) List<Long> initiationIds,
            @RequestParam(required = false) String health) {
        return R.ok(service.projectHealthDashboard(initiationIds, health));
    }
}
