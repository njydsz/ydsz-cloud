package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.service.FlowEfficiencyService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控看板 / 审批效率分析 Controller
 *
 * <p>P0-3 / P2-4 / GAP-P2: 监控概览、异常流程、实例趋势、审批人效率、
 * 流程类型分布、审批效率统计、节点瓶颈、审批趋势、健康度评分
 * （P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-monitor", description = "工作流监控与效率分析接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowMonitorController {

    /** GAP-P2: 审批效率分析服务 */
    private final FlowEfficiencyService efficiencyService;
    /** P2-4: 流程实例 mapper（监控仪表盘聚合查询） */
    private final FlowInstanceMapper instanceMapper;
    /** P1-1: 历史任务 mapper（审批人效率聚合） */
    private final FlowHisTaskMapper hisTaskMapper;
    /** 任务服务（监控概览待办/超期计数） */
    private final FlowTaskService taskService;
    /** 流程实例服务（异常实例详情查询） */
    private final FlowInstanceService instanceService;

    // ============== GAP-P2: 审批效率分析 ==============

    /**
     * GAP-P2: 审批效率统计 — 单量/平均耗时/代批率/超期率
     *
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 统计结果
     */
    @GetMapping("/efficiency/stats")
    public Result<Map<String, Object>> efficiencyStats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(efficiencyService.efficiencyStats(tenantId, startTime, endTime));
    }

    /**
     * GAP-P2: 节点瓶颈排名
     *
     * @param flowCode 流程编码（可选）
     * @param limit    返回条数上限
     * @return 瓶颈节点列表
     */
    @GetMapping("/efficiency/bottleneck")
    public Result<List<Map<String, Object>>> bottleneckRanking(
            @RequestParam(required = false) String flowCode,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(efficiencyService.bottleneckRanking(tenantId, flowCode, limit));
    }

    /**
     * GAP-P2: 审批人效率排名
     *
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param limit     返回条数上限
     * @return 审批人排名列表
     */
    @GetMapping("/efficiency/approver-ranking")
    public Result<List<Map<String, Object>>> approverRanking(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(efficiencyService.approverRanking(tenantId, startTime, endTime, limit));
    }

    /**
     * GAP-P2: 审批趋势
     *
     * @param interval  聚合粒度：DAY / WEEK / MONTH
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 趋势列表
     */
    @GetMapping("/efficiency/trend")
    public Result<List<Map<String, Object>>> approvalTrend(
            @RequestParam(defaultValue = "DAY") String interval,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(efficiencyService.approvalTrend(tenantId, interval, startTime, endTime));
    }

    /**
     * P1: 流程健康度综合评分
     *
     * <p>返回 0-100 分综合评分及 EXCELLENT/GOOD/FAIR/POOR 评级，含各维度扣分明细。
     *
     * @param startTime 开始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 评分结果：score / level / deductions / totalCount / anomalyCount / overdueRate / proxyRate / avgDurationMs
     */
    @Operation(summary = "流程健康度综合评分")
    @GetMapping("/efficiency/health-score")
    public Result<Map<String, Object>> healthScore(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(efficiencyService.healthScore(tenantId, startTime, endTime));
    }

    // ============== P0-3 / P2-4: 监控看板聚合端点 ==============

    /**
     * P0-3 / P2-4: 监控概览 — 聚合实例/任务/效率统计
     *
     * <p>P2-4 修复：前后端契约对齐，字段名与 {@code MonitorOverviewDTO} 一致；
     * 实例状态计数从 5 次 count 查询合并为 1 次 GROUP BY 查询；
     * 新增今日新增/今日完成/待办任务数三项指标。
     *
     * @return 概览统计数据：runningCount/todayNewCount/pendingTaskCount/overdueTaskCount/todayCompletedCount
     */
    @GetMapping("/monitor/overview")
    @PrePermission(PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public Result<Map<String, Object>> monitorOverview() {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        Map<String, Object> overview = new LinkedHashMap<>();

        // P2-4: 1 次 GROUP BY 查询替代 5 次 count（RUNNING/COMPLETED/REJECTED/TERMINATED/SUSPENDED）
        long running = 0;
        try {
            List<Map<String, Object>> statusCounts = instanceMapper.selectCountGroupByStatus(tenantId);
            if (statusCounts != null) {
                for (Map<String, Object> row : statusCounts) {
                    String status = String.valueOf(row.get("flowStatus"));
                    long cnt = ((Number) row.get("cnt")).longValue();
                    if ("RUNNING".equals(status)) running = cnt;
                }
            }
        } catch (Exception e) {
            log.warn("[Monitor] 状态分组计数查询失败: {}", e.getMessage());
        }
        overview.put("runningCount", running);

        // P2-4: 今日新增/今日完成（单次查询）
        try {
            Map<String, Object> today = instanceMapper.selectTodayCount(tenantId);
            if (today != null) {
                overview.put("todayNewCount",
                        today.get("todayNewCount") == null ? 0 : ((Number) today.get("todayNewCount")).longValue());
                overview.put("todayCompletedCount",
                        today.get("todayCompletedCount") == null ? 0 : ((Number) today.get("todayCompletedCount")).longValue());
            } else {
                overview.put("todayNewCount", 0);
                overview.put("todayCompletedCount", 0);
            }
        } catch (Exception e) {
            log.warn("[Monitor] 今日计数查询失败: {}", e.getMessage());
            overview.put("todayNewCount", 0);
            overview.put("todayCompletedCount", 0);
        }

        // P2-4: 待办任务数（PENDING + CLAIMED）
        try {
            overview.put("pendingTaskCount", taskService.countPending(tenantId));
        } catch (Exception e) {
            log.warn("[Monitor] 待办任务计数失败: {}", e.getMessage());
            overview.put("pendingTaskCount", 0);
        }

        // P2-4: 超期任务数
        try {
            overview.put("overdueTaskCount", taskService.countOverdue(null, tenantId));
        } catch (Exception e) {
            log.warn("[Monitor] 超期任务计数失败: {}", e.getMessage());
            overview.put("overdueTaskCount", 0);
        }

        return Result.ok(overview);
    }

    /**
     * P0-3 / P2-4: 异常流程列表 — 超期/卡单/长期运行/高驳回率
     *
     * <p>P2-4 修复：接入 efficiencyService.detectAnomalies() 的完整异常检测能力
     * （卡单/高驳回率节点/长期运行实例），并在前端 DTO 字段对齐。
     *
     * @param anomalyType 异常类型过滤（TIMEOUT/STUCK/REPEATED_REJECT/CIRCULAR_APPROVAL，可空）
     * @param warnLevel   预警级别过滤（RED/YELLOW/ORANGE，可空）
     * @param pageNum     页码（从 1 开始）
     * @param pageSize    每页大小
     * @return 分页异常实例列表
     */
    @GetMapping("/monitor/anomaly")
    @PrePermission(PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public Result<PageResult<Map<String, Object>>> monitorAnomaly(
            @RequestParam(required = false) String anomalyType,
            @RequestParam(required = false) String warnLevel,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");

        // 拉取全量异常（detectAnomalies 默认 limit=100，足够覆盖监控场景）
        List<Map<String, Object>> all = new ArrayList<>();
        try {
            List<Map<String, Object>> detected = efficiencyService.detectAnomalies(tenantId, 100, 24, 7);
            if (detected != null) {
                for (Map<String, Object> a : detected) {
                    Map<String, Object> item = mapAnomaly(a, tenantId);
                    if (item == null) continue;
                    // 类型过滤
                    if (anomalyType != null && !anomalyType.isBlank()
                            && !anomalyType.equals(item.get("anomalyType"))) continue;
                    // 预警级别过滤
                    if (warnLevel != null && !warnLevel.isBlank()
                            && !warnLevel.equals(item.get("warnLevel"))) continue;
                    all.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("[Monitor] 异常检测失败: {}", e.getMessage());
        }

        // 内存分页（数据量小，足够）
        int total = all.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> page = from < to ? all.subList(from, to) : new ArrayList<>();

        return Result.ok(PageResult.of(page, total, pageNum, pageSize));
    }

    /**
     * 将 efficiencyService 返回的异常 Map 映射为前端 AnomalyInstanceDTO 字段
     */
    private Map<String, Object> mapAnomaly(Map<String, Object> a, String tenantId) {
        String type = String.valueOf(a.getOrDefault("type", "UNKNOWN"));
        Map<String, Object> item = new LinkedHashMap<>();
        // 类型映射：HIGH_REJECTION → REPEATED_REJECT；LONG_RUNNING → TIMEOUT；STUCK/OVERDUE 保留
        String anomalyType;
        switch (type) {
            case "STUCK" -> anomalyType = "STUCK";
            case "HIGH_REJECTION" -> anomalyType = "REPEATED_REJECT";
            case "LONG_RUNNING", "OVERDUE" -> anomalyType = "TIMEOUT";
            default -> anomalyType = "TIMEOUT";
        }
        item.put("anomalyType", anomalyType);

        // 实例 ID（卡单场景从 task.instanceId 取，其他从 instanceId 取）
        Object instanceId = a.get("instanceId");
        if (instanceId == null) instanceId = a.get("taskId");
        item.put("id", instanceId == null ? 0 : ((Number) instanceId).longValue());

        // 补实例详情字段（若有 instanceId）
        if (instanceId instanceof Number n) {
            try {
                FlowInstanceDO inst = instanceService.getById(n.longValue());
                if (inst != null) {
                    item.put("flowCode", inst.getFlowCode());
                    item.put("flowName", inst.getFlowName());
                    item.put("title", inst.getTitle());
                    item.put("initiatorName", inst.getInitiatorName());
                    item.put("status", inst.getFlowStatus());
                    item.put("currentNodeName", inst.getCurrentNodeName());
                    item.put("startTime", inst.getStartAt() == null ? null : inst.getStartAt().toString());
                }
            } catch (Exception e) {
                // 实例查询失败不阻塞，降级使用 detectAnomalies 返回的字段
            }
        }
        // 兜底字段（若上面实例查询失败）
        item.putIfAbsent("flowCode", a.get("flowCode"));
        item.putIfAbsent("flowName", a.get("flowName"));
        item.putIfAbsent("currentNodeName", a.get("nodeName") != null ? a.get("nodeName") : a.get("currentNodeName"));

        // 超期天数 / 卡单小时 → 映射为 overdueDays
        Object stuckHours = a.get("stuckHours");
        Object runningDays = a.get("runningDays");
        if (runningDays instanceof Number d) {
            item.put("overdueDays", d.longValue());
        } else if (stuckHours instanceof Number h) {
            item.put("overdueDays", h.longValue() / 24);
        }

        // 预警级别：overdueDays >= 7 → RED；>= 3 → YELLOW；> 0 → ORANGE；卡单/高驳回默认 YELLOW
        long days = item.get("overdueDays") instanceof Number d ? d.longValue() : 0;
        String warnLevel;
        if (anomalyType.equals("TIMEOUT")) {
            if (days >= 7) warnLevel = "RED";
            else if (days >= 3) warnLevel = "YELLOW";
            else warnLevel = "ORANGE";
        } else {
            warnLevel = "YELLOW";  // STUCK / REPEATED_REJECT 默认警告级
        }
        item.put("warnLevel", warnLevel);

        // 描述（用于 tooltip）
        item.put("description", a.get("description"));
        return item;
    }

    /**
     * P0-3 / P2-4: 实例趋势 — 按日期统计新增/完成数
     *
     * <p>P2-4 修复：入参支持 {@code days}（前端 DTO），返回 {@code date/newCount/completedCount} 三字段。
     * 内部按 days 生成日期序列，左连接新增/完成统计补 0。
     *
     * @param days 统计天数（默认 7，可选 30）
     * @return 趋势列表
     */
    @GetMapping("/monitor/instance-trend")
    @PrePermission(PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public Result<List<Map<String, Object>>> monitorInstanceTrend(
            @RequestParam(defaultValue = "7") int days) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        int effectiveDays = (days == 30) ? 30 : 7;

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(effectiveDays - "1");
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = today.atTime(23, 59, 59);

        // 两次 GROUP BY 查询
        List<Map<String, Object>> newCounts = instanceMapper.selectDailyNewCount(tenantId, startDt, endDt);
        List<Map<String, Object>> completedCounts = instanceMapper.selectDailyCompletedCount(tenantId, startDt, endDt);

        // 合并为日期 → {newCount, completedCount}
        Map<String, long[]> byDate = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            byDate.put(start.plusDays(i).toString(), new long[]{0, 0});
        }
        if (newCounts != null) {
            for (Map<String, Object> row : newCounts) {
                String d = String.valueOf(row.get("date"));
                if (byDate.containsKey(d)) {
                    byDate.get(d)[0] = ((Number) row.get("newCount")).longValue();
                }
            }
        }
        if (completedCounts != null) {
            for (Map<String, Object> row : completedCounts) {
                String d = String.valueOf(row.get("date"));
                if (byDate.containsKey(d)) {
                    byDate.get(d)[1] = ((Number) row.get("completedCount")).longValue();
                }
            }
        }

        // 输出按日期排序
        List<Map<String, Object>> result = new ArrayList<>(effectiveDays);
        for (Map.Entry<String, long[]> entry : byDate.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey());
            row.put("newCount", entry.getValue()[0]);
            row.put("completedCount", entry.getValue()[1]);
            result.add(row);
        }
        return Result.ok(result);
    }

    /**
     * P0-3 / P2-4: 审批人效率排名 — SQL GROUP BY 聚合
     *
     * <p>P2-4 修复：直接走 {@code FlowHisTaskMapper.selectApproverEfficiency} SQL 聚合，
     * 替代原 efficiencyService.approverRanking 的 Java 层全表加载聚合。
     * 字段对齐前端 {@code ApproverEfficiencyDTO}：userId/userName/completedCount/avgDurationMs/totalDurationMs。
     *
     * @param topN     返回条数上限
     * @param startTime finish_at 下界（可空）
     * @param endTime   finish_at 上界（可空）
     * @return 审批人排名列表
     */
    @GetMapping("/monitor/approver-efficiency")
    @PrePermission(PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public Result<List<Map<String, Object>>> monitorApproverEfficiency(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        LocalDateTime startDt = parseDateTime(startTime);
        LocalDateTime endDt = parseDateTime(endTime);
        List<Map<String, Object>> rows = hisTaskMapper.selectApproverEfficiency(tenantId, startDt, endDt, topN);

        // 字段重命名：assigneeId(String) → userId(Long) / assigneeName → userName
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                try {
                    item.put("userId", Long.parseLong(String.valueOf(row.get("assigneeId"))));
                } catch (NumberFormatException e) {
                    item.put("userId", 0);
                }
                item.put("userName", row.get("assigneeName"));
                item.put("completedCount", row.get("completedCount"));
                item.put("avgDurationMs", row.get("avgDurationMs"));
                item.put("totalDurationMs", row.get("totalDurationMs"));
                result.add(item);
            }
        }
        return Result.ok(result);
    }

    /**
     * P0-3 / P2-4: 流程类型分布 — SQL GROUP BY 聚合
     *
     * <p>P2-4 修复：从 500 条 Java 层聚合改为 SQL GROUP BY 全量聚合；
     * 返回字段对齐前端 {@code FlowTypeDistributionDTO}：flowCode/flowName/count/percentage。
     *
     * @param startTime start_at 下界（可空）
     * @param endTime   start_at 上界（可空）
     * @return 分布列表
     */
    @GetMapping("/monitor/flow-type-distribution")
    @PrePermission(PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public Result<List<Map<String, Object>>> monitorFlowTypeDistribution(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        LocalDateTime startDt = parseDateTime(startTime);
        LocalDateTime endDt = parseDateTime(endTime);
        List<Map<String, Object>> rows = instanceMapper.selectFlowTypeDistribution(tenantId, startDt, endDt);

        long total = 0;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                total += ((Number) row.get("cnt")).longValue();
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("flowCode", row.get("flowCode"));
                item.put("flowName", row.get("flowName") == null ? row.get("flowCode") : row.get("flowName"));
                long cnt = ((Number) row.get("cnt")).longValue();
                item.put("count", cnt);
                item.put("percentage", total > 0 ? Math.round(cnt * 10000.0 / total) / 100.0 : 0.0);
                result.add(item);
            }
        }
        return Result.ok(result);
    }

    /**
     * P2-4: 解析日期时间字符串（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd）
     */
    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDateTime.parse(str, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            try {
                return LocalDate.parse(str).atStartOfDay();
            } catch (Exception ex) {
                log.warn("[Monitor] 无法解析时间: {}", str);
                return null;
            }
        }
    }
}
