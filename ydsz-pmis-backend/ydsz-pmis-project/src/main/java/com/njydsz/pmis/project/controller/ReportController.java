package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.service.ReportService;
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
 * 基础报表 Controller
 *
 * <p>提供项目利润、成本、回款、生命周期台账及跨项目汇总等报表查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "基础报表")
@RestController
@RequestMapping("/api/v1/execution/report")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService service;

    /**
     * 查询项目利润表
     *
     * @param initiationId 项目立项 ID
     * @param period       所属期间，可选
     * @return 利润报表数据
     */
    @Operation(summary = "项目利润表")
    @PrePermission("report:profit:view")
    @RateLimit(key = "report", qps = 5, windowSeconds = 60)
    @GetMapping("/profit")
    public Result<Map<String, Object>> profit(@RequestParam Long initiationId,
                                         @RequestParam(required = false) String period) {
        return Result.ok(service.projectProfitReport(initiationId, period));
    }

    /**
     * 查询项目成本归集明细表
     *
     * @param initiationId 项目立项 ID
     * @param period       所属期间，可选
     * @return 成本明细报表数据
     */
    @Operation(summary = "项目成本归集明细表")
    @PrePermission("report:cost:view")
    @RateLimit(key = "report", qps = 5, windowSeconds = 60)
    @GetMapping("/cost")
    public Result<Map<String, Object>> cost(@RequestParam Long initiationId,
                                       @RequestParam(required = false) String period) {
        return Result.ok(service.costDetailReport(initiationId, period));
    }

    /**
     * 查询项目回款台账
     *
     * @param initiationId 项目立项 ID
     * @return 回款台账数据
     */
    @Operation(summary = "项目回款台账")
    @PrePermission("report:payment-ledger:view")
    @RateLimit(key = "report", qps = 5, windowSeconds = 60)
    @GetMapping("/payment-ledger")
    public Result<Map<String, Object>> paymentLedger(@RequestParam Long initiationId) {
        return Result.ok(service.paymentLedgerReport(initiationId));
    }

    /**
     * 查询项目全生命周期台账
     *
     * @param initiationId 项目立项 ID
     * @return 生命周期台账数据
     */
    @Operation(summary = "项目全生命周期台账")
    @PrePermission("report:lifecycle:view")
    @RateLimit(key = "report", qps = 5, windowSeconds = 60)
    @GetMapping("/lifecycle")
    public Result<Map<String, Object>> lifecycle(@RequestParam Long initiationId) {
        return Result.ok(service.projectLifecycleReport(initiationId));
    }

    /**
     * 查询跨项目利润汇总
     *
     * @return 利润汇总列表
     */
    @Operation(summary = "跨项目利润汇总")
    @PrePermission("report:profit:view")
    @RateLimit(key = "report", qps = 5, windowSeconds = 60)
    @GetMapping("/profit-summary")
    public Result<List<Map<String, Object>>> profitSummary() {
        return Result.ok(service.profitSummaryAll());
    }

    /**
     * 查询项目利润排行榜
     *
     * @param top    取前 N 条
     * @param sortBy 排序字段
     * @param period 所属期间，可选
     * @return 利润排行列表
     */
    @Operation(summary = "项目利润排行榜（P2-1）")
    @PrePermission("report:profit:view")
    @RateLimit(key = "report", qps = 5, windowSeconds = 60)
    @GetMapping("/profit-rank")
    public Result<List<Map<String, Object>>> profitRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String period) {
        return Result.ok(service.profitRank(top, sortBy, period));
    }
}
