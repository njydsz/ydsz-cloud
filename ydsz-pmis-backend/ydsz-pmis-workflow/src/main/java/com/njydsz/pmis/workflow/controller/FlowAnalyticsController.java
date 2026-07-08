package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.service.FlowAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 审批数据分析 Controller（P2-2）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/analytics")
@RequiredArgsConstructor
@Tag(name = "审批数据分析", description = "审批效率/驳回率/办理人排行等分析仪表盘")
public class FlowAnalyticsController {

    private final FlowAnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "审批总览仪表盘")
    public Result<Object> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(analyticsService.overview(startTime, endTime, TenantContext.getTenantId()));
    }

    @GetMapping("/approver-efficiency")
    @Operation(summary = "办理人效率排行")
    public Result<Object> approverEfficiency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(analyticsService.approverEfficiency(startTime, endTime, TenantContext.getTenantId(), limit));
    }

    @GetMapping("/flow-efficiency")
    @Operation(summary = "流程效率对比")
    public Result<Object> flowEfficiency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(analyticsService.flowEfficiencyComparison(startTime, endTime, TenantContext.getTenantId()));
    }

    @GetMapping("/node-duration")
    @Operation(summary = "节点耗时分析")
    public Result<Object> nodeDuration(@RequestParam String flowCode) {
        return Result.ok(analyticsService.nodeDurationStats(flowCode, TenantContext.getTenantId()));
    }

    @GetMapping("/approval-trend")
    @Operation(summary = "审批趋势分析")
    public Result<Object> approvalTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "DAY") String granularity) {
        return Result.ok(analyticsService.approvalTrend(startTime, endTime, TenantContext.getTenantId(), granularity));
    }
}
