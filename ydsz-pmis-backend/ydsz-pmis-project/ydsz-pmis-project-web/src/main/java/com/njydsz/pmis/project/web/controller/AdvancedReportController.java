package com.njydsz.pmis.project.web.controller.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.project.server.service.AdvancedReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 高级报表 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "高级报表")
@RestController
@RequestMapping("/api/project/report/advanced")
@RequiredArgsConstructor
@Validated
public class AdvancedReportController {

    /** 高级报表服务 */
    private final AdvancedReportService service;

    @Operation(summary = "EVM 挣值管理报表")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/evm")
    public BaseResponse<List<Map<String, Object>>> evm(@RequestParam String initiationId) {
        return BaseResponse.ok(service.evmReport(initiationId));
    }

    @Operation(summary = "人效排行榜（默认近 3 个月）")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/utilizationRank")
    public BaseResponse<List<Map<String, Object>>> utilizationRank(
            @RequestParam(defaultValue = "20") int top) {
        return BaseResponse.ok(service.utilizationRank(top));
    }

    @Operation(summary = "人效排行榜（自定义时间窗口/事业部）")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/utilizationRank/range")
    public BaseResponse<List<Map<String, Object>>> utilizationRankRange(
            @RequestParam(defaultValue = "20") int top,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String department) {
        return BaseResponse.ok(service.utilizationRank(top, from, to, department));
    }

    @Operation(summary = "单员工可计费利用率")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/utilization/employee")
    public BaseResponse<Map<String, Object>> utilizationOf(
            @RequestParam String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return BaseResponse.ok(service.utilizationOf(employeeId, from, to));
    }

    @Operation(summary = "事业部级可计费利用率")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/utilization/department")
    public BaseResponse<List<Map<String, Object>>> utilizationByDepartment(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return BaseResponse.ok(service.utilizationByDepartment(from, to));
    }

    @Operation(summary = "Bench 闲置成本报表（默认近 30 天）")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/benchCost")
    public BaseResponse<List<Map<String, Object>>> benchCost() {
        return BaseResponse.ok(service.benchCostReport());
    }

    @Operation(summary = "Bench 闲置成本报表（自定义时间窗口）")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/benchCost/range")
    public BaseResponse<List<Map<String, Object>>> benchCostRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return BaseResponse.ok(service.benchCostReport(from, to));
    }

    @Operation(summary = "双费率利润对比表")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/dualRate")
    public BaseResponse<List<Map<String, Object>>> dualRate(@RequestParam(required = false) String period) {
        return BaseResponse.ok(service.dualRateProfitCompare(period));
    }

    @Operation(summary = "资源负载甘特图")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/gantt")
    public BaseResponse<List<Map<String, Object>>> gantt(@RequestParam String initiationId) {
        return BaseResponse.ok(service.resourceGantt(initiationId));
    }

    @Operation(summary = "项目风险预警看板")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/riskDashboard")
    public BaseResponse<List<Map<String, Object>>> riskDashboard() {
        return BaseResponse.ok(service.riskDashboard());
    }

    @Operation(summary = "项目风险矩阵热力图（P2-2）")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/riskMatrix")
    public BaseResponse<Map<String, Object>> riskMatrix(
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(service.riskMatrix(initiationId, riskType, status));
    }

    @Operation(summary = "资源占用趋势图 双 Y 轴（P2-3）")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/utilizationTrend")
    public BaseResponse<Map<String, Object>> utilizationTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String department) {
        return BaseResponse.ok(service.resourceUtilizationTrend(from, to, department));
    }

    @Operation(summary = "项目健康仪表盘 CPI/SPI/毛利率（P2-5）")
    @AuthApiPermission(apiCodes = "report:advanced:view")
    @GetMapping("/projectHealthDashboard")
    public BaseResponse<Map<String, Object>> projectHealthDashboard(
            @RequestParam(required = false) List<String> initiationIds,
            @RequestParam(required = false) String health) {
        return BaseResponse.ok(service.projectHealthDashboard(initiationIds, health));
    }
}
