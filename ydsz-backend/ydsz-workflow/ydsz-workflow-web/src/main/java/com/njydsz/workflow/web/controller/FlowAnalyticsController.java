package com.njydsz.workflow.web.controller.analytics;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.security.TenantContext;
import com.njydsz.workflow.server.service.FlowAnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批数据分析 Controller（P2-2）。
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/analytics")
@RequiredArgsConstructor
@Tag(name = "审批数据分析", description = "审批效率/驳回率/办理人排行等分析仪表盘")
public class FlowAnalyticsController {

    /** 审批数据分析服务，提供效率排行、趋势分析等统计能力 */
    private final FlowAnalyticsService analyticsService;

    /**
     * 审批总览仪表盘。
     *
     * @param startTime 查询起始时间（可选）
     * @param endTime   查询截止时间（可选）
     * @return 总览统计数据
     */
    @GetMapping("/overview")
    @Operation(summary = "审批总览仪表盘")
    public BaseResponse<Object> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return BaseResponse.success(analyticsService.overview(startTime, endTime, TenantContext.getTenantId()));
    }

    /**
     * 办理人效率排行。
     *
     * @param startTime 查询起始时间（可选）
     * @param endTime   查询截止时间（可选）
     * @param limit     返回条数上限，默认 20
     * @return 办理人效率排行列表
     */
    @GetMapping("/approverEfficiency")
    @Operation(summary = "办理人效率排行")
    public BaseResponse<Object> approverEfficiency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "20") int limit) {
        return BaseResponse.success(analyticsService.approverEfficiency(startTime, endTime, TenantContext.getTenantId(), limit));
    }

    /**
     * 流程效率对比。
     *
     * @param startTime 查询起始时间（可选）
     * @param endTime   查询截止时间（可选）
     * @return 各流程效率对比数据
     */
    @GetMapping("/flowEfficiency")
    @Operation(summary = "流程效率对比")
    public BaseResponse<Object> flowEfficiency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return BaseResponse.success(analyticsService.flowEfficiencyComparison(startTime, endTime, TenantContext.getTenantId()));
    }

    /**
     * 节点耗时分析。
     *
     * @param flowCode 流程编码
     * @return 各节点耗时统计数据
     */
    @GetMapping("/nodeDuration")
    @Operation(summary = "节点耗时分析")
    public BaseResponse<Object> nodeDuration(@RequestParam String flowCode) {
        return BaseResponse.success(analyticsService.nodeDurationStats(flowCode, TenantContext.getTenantId()));
    }

    /**
     * 审批趋势分析。
     *
     * @param startTime  查询起始时间（可选）
     * @param endTime    查询截止时间（可选）
     * @param granularity 统计粒度，默认 DAY
     * @return 审批趋势时间序列数据
     */
    @GetMapping("/approvalTrend")
    @Operation(summary = "审批趋势分析")
    public BaseResponse<Object> approvalTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "DAY") String granularity) {
        return BaseResponse.success(analyticsService.approvalTrend(startTime, endTime, TenantContext.getTenantId(), granularity));
    }
}
