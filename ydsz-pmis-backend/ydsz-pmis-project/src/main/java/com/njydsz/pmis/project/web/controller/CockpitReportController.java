package com.njydsz.pmis.project.web.controller.report;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.domain.dto.CockpitAlertSummaryVO;
import com.njydsz.pmis.project.domain.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.project.domain.dto.CockpitKpiVO;
import com.njydsz.pmis.project.domain.dto.ExecutiveOverviewVO;
import com.njydsz.pmis.project.domain.dto.KpiTrendVO;
import com.njydsz.pmis.project.domain.dto.ProjectGroupKpiDTO;
import com.njydsz.pmis.project.server.service.CockpitReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/report/cockpit")
@RequiredArgsConstructor
@Validated
public class CockpitReportController {

    /** 经营驾驶舱报表服务 */
    private final CockpitReportService service;

    /**
     * 驾驶舱总览 KPI
     *
     * @param period    所属期间，可选
     * @param drillDown 下钻参数
     * @return 总览 KPI 数据
     */
    @Operation(summary = "驾驶舱总览 KPI")
    @PrePermission("cockpit:overview:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/overview")
    public Result<CockpitKpiVO> overview(@RequestParam(required = false) String period,
                                     CockpitDrillDownDTO drillDown) {
        return Result.ok(service.overview(period, drillDown));
    }

    /**
     * EVM 健康分布
     *
     * @param period    所属期间，可选
     * @param drillDown 下钻参数
     * @return EVM 健康分布（红/黄/绿计数）
     */
    @Operation(summary = "EVM 健康分布")
    @PrePermission("cockpit:overview:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/evmHealth")
    public Result<Map<String, Integer>> evmHealth(@RequestParam(required = false) String period,
                                             CockpitDrillDownDTO drillDown) {
        return Result.ok(service.evmHealthDistribution(period, drillDown));
    }

    /**
     * Bench 闲置成本汇总
     *
     * @param drillDown 下钻参数
     * @return Bench 闲置成本汇总数据
     */
    @Operation(summary = "Bench 闲置成本汇总")
    @PrePermission("cockpit:overview:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/benchCost")
    public Result<Map<String, Object>> benchCost(CockpitDrillDownDTO drillDown) {
        return Result.ok(service.benchCostSummary(drillDown));
    }

    /**
     * 可计费利用率汇总
     *
     * @param drillDown 下钻参数
     * @return 可计费利用率汇总数据
     */
    @Operation(summary = "可计费利用率汇总")
    @PrePermission("cockpit:overview:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/utilization")
    public Result<Map<String, Object>> utilization(CockpitDrillDownDTO drillDown) {
        return Result.ok(service.utilizationSummary(drillDown));
    }

    /**
     * 按事业部下钻
     *
     * @param period 所属期间，可选
     * @return 各事业部 KPI 明细列表
     */
    @Operation(summary = "按事业部下钻")
    @PrePermission("cockpit:drilldown:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/drill/dept")
    public Result<List<Map<String, Object>>> drillDept(@RequestParam(required = false) String period) {
        return Result.ok(service.drillByDept(period));
    }

    /**
     * 按项目类型下钻
     *
     * @param period 所属期间，可选
     * @return 各项目类型 KPI 明细列表
     */
    @Operation(summary = "按项目类型下钻")
    @PrePermission("cockpit:drilldown:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/drill/projectType")
    public Result<List<Map<String, Object>>> drillProjectType(@RequestParam(required = false) String period) {
        return Result.ok(service.drillByProjectType(period));
    }

    /**
     * 按客户下钻
     *
     * @param period 所属期间，可选
     * @return 各客户 KPI 明细列表
     */
    @Operation(summary = "按客户下钻")
    @PrePermission("cockpit:drilldown:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/drill/customer")
    public Result<List<Map<String, Object>>> drillCustomer(@RequestParam(required = false) String period) {
        return Result.ok(service.drillByCustomer(period));
    }

    /**
     * 合同总额年度趋势
     *
     * @return 合同总额年度趋势数据
     */
    @Operation(summary = "合同总额年度趋势")
    @PrePermission("cockpit:overview:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/contractYearlyTrend")
    public Result<Map<String, Object>> contractYearlyTrend() {
        return Result.ok(service.contractAmountYearlyTrend());
    }

    // ========== 批次18 增量端点 ==========

    /**
     * 预警事件摘要
     *
     * @param period    所属期间，可选
     * @param drillDown 下钻参数
     * @return 预警事件摘要数据
     */
    @Operation(summary = "预警事件摘要（批次18）")
    @PrePermission("cockpit:alert:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/alerts")
    public Result<CockpitAlertSummaryVO> alerts(@RequestParam(required = false) String period,
                                            CockpitDrillDownDTO drillDown) {
        return Result.ok(service.alertSummary(period, drillDown));
    }

    /**
     * 项目群驾驶舱
     *
     * @param period    所属期间，可选
     * @param drillDown 下钻参数
     * @return 项目群 KPI 列表
     */
    @Operation(summary = "项目群驾驶舱（批次18）")
    @PrePermission("cockpit:overview:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/projectGroup")
    public Result<List<ProjectGroupKpiDTO>> projectGroup(@RequestParam(required = false) String period,
                                                      CockpitDrillDownDTO drillDown) {
        return Result.ok(service.projectGroupOverview(period, drillDown));
    }

    /**
     * 高管看板
     *
     * @param period    所属期间，可选
     * @param drillDown 下钻参数
     * @return 高管看板数据
     */
    @Operation(summary = "高管看板（批次18）")
    @PrePermission("cockpit:overview:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/executive")
    public Result<ExecutiveOverviewVO> executive(@RequestParam(required = false) String period,
                                             CockpitDrillDownDTO drillDown) {
        return Result.ok(service.executiveOverview(period, drillDown));
    }

    /**
     * KPI 趋势（最近 N 个月）
     *
     * @param months 月份数量，默认 12
     * @return KPI 趋势数据
     */
    @Operation(summary = "KPI 趋势（最近 N 个月，批次18）")
    @PrePermission("cockpit:overview:view")
    @RateLimit(key = "cockpit", qps = 5, windowSeconds = 60)
    @GetMapping("/kpiTrend")
    public Result<KpiTrendVO> kpiTrend(@RequestParam(required = false, defaultValue = "12") Integer months) {
        return Result.ok(service.kpiTrend(months));
    }
}
