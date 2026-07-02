package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "基础报表")
@RestController
@RequestMapping("/api/v1/execution/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @Operation(summary = "项目利润表")
    @PrePermission("report:profit:view")
    @GetMapping("/profit")
    public Result<Map<String, Object>> profit(@RequestParam Long initiationId,
                                         @RequestParam(required = false) String period) {
        return Result.ok(service.projectProfitReport(initiationId, period));
    }

    @Operation(summary = "项目成本归集明细表")
    @PrePermission("report:cost:view")
    @GetMapping("/cost")
    public Result<Map<String, Object>> cost(@RequestParam Long initiationId,
                                       @RequestParam(required = false) String period) {
        return Result.ok(service.costDetailReport(initiationId, period));
    }

    @Operation(summary = "项目回款台账")
    @PrePermission("report:payment-ledger:view")
    @GetMapping("/payment-ledger")
    public Result<Map<String, Object>> paymentLedger(@RequestParam Long initiationId) {
        return Result.ok(service.paymentLedgerReport(initiationId));
    }

    @Operation(summary = "项目全生命周期台账")
    @PrePermission("report:lifecycle:view")
    @GetMapping("/lifecycle")
    public Result<Map<String, Object>> lifecycle(@RequestParam Long initiationId) {
        return Result.ok(service.projectLifecycleReport(initiationId));
    }

    @Operation(summary = "跨项目利润汇总")
    @PrePermission("report:profit:view")
    @GetMapping("/profit-summary")
    public Result<List<Map<String, Object>>> profitSummary() {
        return Result.ok(service.profitSummaryAll());
    }

    @Operation(summary = "项目利润排行榜（P2-1）")
    @PrePermission("report:profit:view")
    @GetMapping("/profit-rank")
    public Result<List<Map<String, Object>>> profitRank(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String period) {
        return Result.ok(service.profitRank(top, sortBy, period));
    }
}
