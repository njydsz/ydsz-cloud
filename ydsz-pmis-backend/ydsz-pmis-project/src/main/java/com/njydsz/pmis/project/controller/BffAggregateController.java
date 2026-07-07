package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.project.dto.CockpitAlertSummaryVO;
import com.njydsz.pmis.project.dto.CockpitKpiVO;
import com.njydsz.pmis.project.service.CockpitReportService;
import com.njydsz.pmis.project.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BFF 聚合 Controller。
 *
 * <p>一次请求返回复合数据，减少前端网络往返。
 * 聚合多维度数据（立项 / EVM / 合同 / WBS / KPI / 告警 / 待办），
 * 各维度独立 try-catch，单维度异常不影响其他维度返回。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/execution/aggregate")
@RequiredArgsConstructor
@Validated
@Tag(name = "BFF聚合", description = "前端聚合接口，一次请求返回复合数据")
public class BffAggregateController {

    private final CockpitReportService cockpitReportService;
    private final ReportService reportService;

    @GetMapping("/project-detail/{initiationId}")
    @RateLimit(key = "bff", qps = 20, windowSeconds = 60)
    @Operation(summary = "项目详情聚合", description = "一次返回立项+合同+WBS概览+EVM摘要")
    public Map<String, Object> projectDetailAggregate(
            @PathVariable @NotNull(message = "{validation.execution.msg_1d72f14c}") String initiationId) {
        Map<String, Object> result = new HashMap<>();
        // 聚合多维度数据，减少前端多次请求
        try {
            // 立项信息（全生命周期台账：商机 → 立项 → 合同 → 变更 → 结项）
            result.put("initiation", reportService.projectLifecycleReport(initiationId));
        } catch (Exception e) {
            log.warn("聚合查询立项信息失败, initiationId={}", initiationId, e);
            result.put("initiation", Map.of("error", e.getMessage()));
        }
        try {
            // EVM 摘要（利润表含 CPI/SPI 等挣值指标）
            result.put("evm", reportService.projectProfitReport(initiationId, null));
        } catch (Exception e) {
            log.warn("聚合查询 EVM 数据失败, initiationId={}", initiationId, e);
            result.put("evm", Map.of("error", e.getMessage()));
        }
        try {
            // 合同 / 回款台账列表
            result.put("contracts", reportService.paymentLedgerReport(initiationId));
        } catch (Exception e) {
            log.warn("聚合查询合同台账失败, initiationId={}", initiationId, e);
            result.put("contracts", List.of());
        }
        try {
            // WBS 概览（成本归集明细含人力/采购/费用/分摊拆解）
            result.put("wbsOverview", reportService.costDetailReport(initiationId, null));
        } catch (Exception e) {
            log.warn("聚合查询 WBS 概览失败, initiationId={}", initiationId, e);
            result.put("wbsOverview", Map.of("error", e.getMessage()));
        }
        return result;
    }

    @GetMapping("/dashboard-summary")
    @RateLimit(key = "bff", qps = 20, windowSeconds = 60)
    @Operation(summary = "首页仪表盘聚合", description = "一次返回KPI+图表+待办数据")
    public Map<String, Object> dashboardSummary(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // KPI 核心指标（驾驶舱总览）
            CockpitKpiVO kpi = cockpitReportService.overview(null, null);
            result.put("kpi", kpi);
        } catch (Exception e) {
            log.warn("聚合查询 KPI 数据失败", e);
            result.put("kpi", Map.of("error", e.getMessage()));
        }
        try {
            // 告警事件摘要（严重度计数 + 顶部事件）
            CockpitAlertSummaryVO alerts = cockpitReportService.alertSummary(null, null);
            result.put("alerts", alerts);
        } catch (Exception e) {
            log.warn("聚合查询告警摘要失败", e);
            result.put("alerts", List.of());
        }
        try {
            // 待办计数（当前模块无独立待办服务，返回空列表占位，保证聚合结构完整）
            result.put("todos", List.of());
        } catch (Exception e) {
            log.warn("聚合查询待办数据失败", e);
            result.put("todos", List.of());
        }
        return result;
    }
}
