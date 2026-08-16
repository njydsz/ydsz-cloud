package com.njydsz.workflow.web.controller.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowEfficiencyService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 监控看板 Controller
 *
 * <p>业务背景：工作流「运营管理」HTTP 入口，对标钉钉 / 飞书审批后台的「监控中心」。
 * 为运维人员提供流程实例的实时状态监控、异常流程识别、趋势分析、类型分布、
 * 超期任务排行、审批人负载分布、流程效率对比、首屏仪表盘聚合等能力。
 *
 * <p>核心能力：
 * <ul>
 *   <li><b>概览</b>：{@code GET /monitor/overview}（运行中 / 超期 / 今日新增 / 完成数）</li>
 *   <li><b>异常流程</b>：{@code GET /monitor/anomaly}（超期 / 挂起超 24h / 失败流程）</li>
 *   <li><b>实例趋势</b>：{@code GET /monitor/instanceTrend}（N 天内每日新增 / 完成）</li>
 *   <li><b>审批人效率</b>：{@code GET /monitor/approverEfficiency}（人均审批耗时 / 排名）</li>
 *   <li><b>类型分布</b>：{@code GET /monitor/flowTypeDistribution}（按流程类型聚合占比）</li>
 *   <li><b>仪表盘聚合</b>：{@code GET /monitor/dashboard}（首屏一次加载全部指标）</li>
 *   <li><b>超期任务</b>：{@code GET /monitor/overdueTasks}（超期任务 Top N 排行）</li>
 *   <li><b>审批人负载</b>：{@code GET /monitor/approverWorkload}（审批人待办数量分布）</li>
 *   <li><b>流程效率对比</b>：{@code GET /monitor/flowEfficiencyComparison}（按流程编码分组聚合）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>所有接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_MONITOR_VIEW} 权限码；只读、不写。
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>趋势 / 分布类查询走 OLAP 聚合（{@code ydsz_flow_instance} 复合索引）</li>
 *   <li>异常检测走 {@link FlowEfficiencyService}，单 SQL 聚合减少 DB 压力</li>
 *   <li>仪表盘聚合各子模块独立降级，单点失败不阻塞整体响应</li>
 * </ul>
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传、日期范围解析、VO 转换；
 * 监控指标计算下沉到 {@link FlowEfficiencyService} / {@link FlowInstanceService} / {@link FlowTaskService}。
 *
 * <p>从原 {@code FlowMonitorController} 拆分而来，与 {@link FlowEfficiencyController}
 * 共享基路径 {@code /api/v1/workflow/engine}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowEfficiencyService 效率分析服务
 * @see FlowInstanceService 流程实例服务
 * @see FlowTaskService 任务服务
 * @see FlowEfficiencyController 审批效率分析 Controller
 */
@Slf4j
@RestController
@Tag(name = "workflow-monitor", description = "工作流监控看板接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowMonitorDashboardController {

    /** GAP-P2: 审批效率分析服务（异常检测 / 效率统计 / 健康度评分） */
    private final FlowEfficiencyService efficiencyService;
    /** P2-4: 流程实例 mapper（监控仪表盘聚合查询） */
    private final FlowInstanceMapper instanceMapper;
    /** P1-1: 历史任务 mapper（审批人效率聚合） */
    private final FlowHisTaskMapper hisTaskMapper;
    /** 任务服务（监控概览待办/超期计数） */
    private final FlowTaskService taskService;
    /** 流程实例服务（异常实例详情查询） */
    private final FlowInstanceService instanceService;
    /** P2-7: 待办任务 mapper（超期任务 TopN / 审批人负载分布） */
    private final FlowRunTaskMapper runTaskMapper;

    // ============== P0-3 / P2-4: 监控看板聚合端点 ==============

    /**
     * P0-3 / P2-4: 监控概览 — 聚合实例/任务/效率统计。
     *
     * <p>P2-4 修复：前后端契约对齐，字段名与 {@code MonitorOverviewDTO} 一致；
     * 实例状态计数从 5 次 count 查询合并为 1 次 GROUP BY 查询；
     * 新增今日新增/今日完成/待办任务数三项指标。
     *
     * @return 概览统计数据：runningCount/todayNewCount/pendingTaskCount/overdueTaskCount/todayCompletedCount
     */
    @GetMapping("/monitor/overview")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public BaseResponse<Map<String, Object>> monitorOverview() {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();
        return BaseResponse.success(buildOverview(tenantId));
    }

    /**
     * P0-3 / P2-4: 异常流程列表 — 超期/卡单/长期运行/高驳回率。
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
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public BaseResponse<List<Map<String, Object>>> monitorAnomaly(
            @RequestParam(required = false) String anomalyType,
            @RequestParam(required = false) String warnLevel,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();

        // 拉取全量异常（detectAnomalies 默认 limit=100，足够覆盖监控场景）
        List<Map<String, Object>> all = new ArrayList<>(100);
        try {
            List<Map<String, Object>> detected = efficiencyService.detectAnomalies(tenantId, 100, 24, 7);
            if (detected != null) {
                for (Map<String, Object> a : detected) {
                    Map<String, Object> item = mapAnomaly(a);
                    if (item == null) {
                        continue;
                    }
                    // 类型过滤
                    if (anomalyType != null && !anomalyType.isBlank()
                            && !anomalyType.equals(item.get("anomalyType"))) {
                        continue;
                    }
                    // 预警级别过滤
                    if (warnLevel != null && !warnLevel.isBlank()
                            && !warnLevel.equals(item.get("warnLevel"))) {
                        continue;
                    }
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

        return PageResponse.success((long) total, (long) pageNum, (long) pageSize, page);
    }

    /**
     * P0-3 / P2-4: 实例趋势 — 按日期统计新增/完成数。
     *
     * <p>P2-4 修复：入参支持 {@code days}（前端 DTO），返回 {@code date/newCount/completedCount} 三字段。
     * 内部按 days 生成日期序列，左连接新增/完成统计补 0。
     *
     * @param days 统计天数（默认 7，可选 30）
     * @return 趋势列表
     */
    @GetMapping("/monitor/instanceTrend")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public BaseResponse<List<Map<String, Object>>> monitorInstanceTrend(
            @RequestParam(defaultValue = "7") int days) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();
        return BaseResponse.success(buildInstanceTrend(tenantId, days));
    }

    /**
     * P0-3 / P2-4: 审批人效率排名 — SQL GROUP BY 聚合。
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
    @GetMapping("/monitor/approverEfficiency")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public BaseResponse<List<Map<String, Object>>> monitorApproverEfficiency(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();
        LocalDateTime startDt = parseDateTime(startTime);
        LocalDateTime endDt = parseDateTime(endTime);
        List<Map<String, Object>> rows = hisTaskMapper.selectApproverEfficiency(tenantId, startDt, endDt, topN);

        // 字段重命名：assigneeId(String) → userId(Long) / assigneeName → userName
        List<Map<String, Object>> result = new ArrayList<>(topN);
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
        return BaseResponse.success(result);
    }

    /**
     * P0-3 / P2-4: 流程类型分布 — SQL GROUP BY 聚合。
     *
     * <p>P2-4 修复：从 500 条 Java 层聚合改为 SQL GROUP BY 全量聚合；
     * 返回字段对齐前端 {@code FlowTypeDistributionDTO}：flowCode/flowName/count/percentage。
     *
     * @param startTime start_at 下界（可空）
     * @param endTime   start_at 上界（可空）
     * @return 分布列表
     */
    @GetMapping("/monitor/flowTypeDistribution")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public BaseResponse<List<Map<String, Object>>> monitorFlowTypeDistribution(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();
        LocalDateTime startDt = parseDateTime(startTime);
        LocalDateTime endDt = parseDateTime(endTime);
        List<Map<String, Object>> rows = instanceMapper.selectFlowTypeDistribution(tenantId, startDt, endDt);

        long total = 0;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                total += toLong(row.get("cnt"));
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(32);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("flowCode", row.get("flowCode"));
                item.put("flowName", row.get("flowName") == null ? row.get("flowCode") : row.get("flowName"));
                long cnt = toLong(row.get("cnt"));
                item.put("count", cnt);
                item.put("percentage", total > 0 ? Math.round(cnt * 10000.0 / total) / 100.0 : 0.0);
                result.add(item);
            }
        }
        return BaseResponse.success(result);
    }

    // ============== P2-7: 监控仪表盘 UI 增强 ==============

    /**
     * P2-7: 仪表盘聚合端点 — 一次性返回概览 + 7 天趋势 + 异常 Top5 + 效率统计。
     *
     * <p>对标钉钉/飞书审批中心首页仪表盘。前端首屏加载仅需一次请求，避免 N 次 HTTP 往返。
     * 聚合内容：
     * <ul>
     *   <li>overview — 运行中/今日新增/今日完成/待办/超期计数（复用 buildOverview 逻辑）</li>
     *   <li>instanceTrend — 近 7 天新增/完成趋势</li>
     *   <li>overdueTop5 — 超期最严重的 5 个任务</li>
     *   <li>anomalyTop5 — 异常实例 Top5（复用 efficiencyService.detectAnomalies）</li>
     *   <li>efficiency — 效率统计（单量/平均耗时/代批率/超期率）</li>
     *   <li>healthScore — 健康度评分</li>
     * </ul>
     *
     * <p>各子模块查询失败时降级返回空值/默认值，不阻塞其他模块。
     *
     * @return 仪表盘聚合数据
     */
    @GetMapping("/monitor/dashboard")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    @Operation(summary = "监控仪表盘聚合数据（首屏一次加载）")
    public BaseResponse<Map<String, Object>> monitorDashboard() {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();
        Map<String, Object> dashboard = new LinkedHashMap<>();

        // 1. overview
        try {
            dashboard.put("overview", buildOverview(tenantId));
        } catch (Exception e) {
            log.warn("[Dashboard] overview 聚合失败: {}", e.getMessage());
            dashboard.put("overview", new LinkedHashMap<>());
        }

        // 2. instanceTrend（近 7 天）
        try {
            dashboard.put("instanceTrend", buildInstanceTrend(tenantId, 7));
        } catch (Exception e) {
            log.warn("[Dashboard] instanceTrend 聚合失败: {}", e.getMessage());
            dashboard.put("instanceTrend", new ArrayList<>());
        }

        // 3. overdueTop5
        try {
            List<Map<String, Object>> overdueTop = runTaskMapper.selectOverdueTopN(tenantId, 5);
            dashboard.put("overdueTop5", overdueTop != null ? overdueTop : new ArrayList<>());
        } catch (Exception e) {
            log.warn("[Dashboard] overdueTop5 查询失败: {}", e.getMessage());
            dashboard.put("overdueTop5", new ArrayList<>());
        }

        // 4. anomalyTop5
        try {
            List<Map<String, Object>> anomalies = efficiencyService.detectAnomalies(tenantId, 5, 24, 7);
            dashboard.put("anomalyTop5", anomalies != null ? anomalies : new ArrayList<>());
        } catch (Exception e) {
            log.warn("[Dashboard] anomalyTop5 查询失败: {}", e.getMessage());
            dashboard.put("anomalyTop5", new ArrayList<>());
        }

        // 5. efficiency
        try {
            dashboard.put("efficiency", efficiencyService.efficiencyStats(tenantId, null, null));
        } catch (Exception e) {
            log.warn("[Dashboard] efficiency 查询失败: {}", e.getMessage());
            dashboard.put("efficiency", new LinkedHashMap<>());
        }

        // 6. healthScore
        try {
            dashboard.put("healthScore", efficiencyService.healthScore(tenantId, null, null));
        } catch (Exception e) {
            log.warn("[Dashboard] healthScore 查询失败: {}", e.getMessage());
            dashboard.put("healthScore", new LinkedHashMap<>());
        }

        return BaseResponse.success(dashboard);
    }

    /**
     * P2-7: 超期任务 Top N 排行 — 按超期时长降序返回最严重的超期任务。
     *
     * <p>对标钉钉/飞书审批中心"超期任务"看板。与 {@link #monitorAnomaly} 的区别：
     * monitorAnomaly 返回异常实例（TIMEOUT/STUCK/REPEATED_REJECT），本端点专注超期任务维度，
     * 按超期时长（overdueHours = now - dueAt）降序，直接返回任务级明细。
     *
     * @param limit 返回条数上限（默认 10，最大 100）
     * @return 超期任务列表：taskId / instanceId / flowCode / flowName / title / nodeName /
     * assigneeId / assigneeName / dueAt / overdueHours / urgeCount
     */
    @GetMapping("/monitor/overdueTasks")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    @Operation(summary = "超期任务 Top N 排行（按超期时长降序）")
    public BaseResponse<List<Map<String, Object>>> monitorOverdueTasks(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();
        List<Map<String, Object>> rows = runTaskMapper.selectOverdueTopN(tenantId, limit);
        return BaseResponse.success(rows != null ? rows : new ArrayList<>());
    }

    /**
     * P2-7: 审批人负载分布 — 统计各审批人当前待办数量（PENDING + CLAIMED）。
     *
     * <p>对标钉钉/飞书"审批人负载"看板，用于识别负载不均、优化审批人配置。
     * 返回字段：assigneeId / assigneeName / pendingCount / claimedCount / totalCount / overdueCount。
     *
     * @param limit 返回条数上限（默认 10，最大 100）
     * @return 审批人负载列表，按 totalCount 降序
     */
    @GetMapping("/monitor/approverWorkload")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    @Operation(summary = "审批人负载分布（当前待办数量）")
    public BaseResponse<List<Map<String, Object>>> monitorApproverWorkload(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();
        List<Map<String, Object>> rows = runTaskMapper.selectWorkloadByAssignee(tenantId, limit);
        return BaseResponse.success(rows != null ? rows : new ArrayList<>());
    }

    /**
     * P2-7: 流程效率对比 — 按流程编码分组聚合效率指标。
     *
     * <p>对标钉钉/飞书"流程效率对比"看板。用于横向对比不同流程类型的效率：
     * <ul>
     *   <li>totalCount — 任务总数（COMPLETED + REJECTED）</li>
     *   <li>completedCount — 通过数</li>
     *   <li>rejectedCount — 驳回数</li>
     *   <li>rejectionRate — 驳回率（0~1）</li>
     *   <li>avgDurationMs — 平均处理耗时（毫秒，仅 COMPLETED）</li>
     * </ul>
     *
     * @param startTime finish_at 下界（可空，格式 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd）
     * @param endTime   finish_at 上界（可空）
     * @return 流程效率对比列表，按 totalCount 降序
     */
    @GetMapping("/monitor/flowEfficiencyComparison")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    @Operation(summary = "流程效率对比（按流程编码分组）")
    public BaseResponse<List<Map<String, Object>>> monitorFlowEfficiencyComparison(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault();
        LocalDateTime startDt = parseDateTime(startTime);
        LocalDateTime endDt = parseDateTime(endTime);
        List<Map<String, Object>> rows = hisTaskMapper.selectFlowEfficiencyComparison(tenantId, startDt, endDt);
        return BaseResponse.success(rows != null ? rows : new ArrayList<>());
    }

    // ============== 私有辅助方法 ==============

    /**
     * 将 efficiencyService 返回的异常 Map 映射为前端 AnomalyInstanceDTO 字段。
     *
     * @param a 原始异常数据
     * @return 映射后的前端 DTO 结构
     */
    private Map<String, Object> mapAnomaly(Map<String, Object> a) {
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
        if (instanceId == null) {
            instanceId = a.get("taskId");
        }
        item.put("id", instanceId == null ? 0 : toLong(instanceId));

        // 补实例详情字段（若有 instanceId）
        if (instanceId instanceof Number n) {
            try {
                FlowInstance inst = instanceService.getById(String.valueOf(n.longValue()));
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
            if (days >= 7) {
                warnLevel = "RED";
            } else if (days >= 3) {
                warnLevel = "YELLOW";
            } else {
                warnLevel = "ORANGE";
            }
        } else {
            warnLevel = "YELLOW";  // STUCK / REPEATED_REJECT 默认警告级
        }
        item.put("warnLevel", warnLevel);

        // 描述（用于 tooltip）
        item.put("description", a.get("description"));
        return item;
    }

    /**
     * P2-7: 构建监控概览数据（复用 monitorOverview 逻辑，供 dashboard 聚合调用）。
     *
     * @param tenantId 租户 ID
     * @return 概览统计数据 Map
     */
    private Map<String, Object> buildOverview(String tenantId) {
        Map<String, Object> overview = new LinkedHashMap<>();
        long running = 0;
        try {
            List<Map<String, Object>> statusCounts = instanceMapper.selectCountGroupByStatus(tenantId);
            if (statusCounts != null) {
                for (Map<String, Object> row : statusCounts) {
                    String status = String.valueOf(row.get("flowStatus"));
                    long cnt = toLong(row.get("cnt"));
                    if ("RUNNING".equals(status)) {
                        running = cnt;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Dashboard] 状态分组计数查询失败: {}", e.getMessage());
        }
        overview.put("runningCount", running);

        try {
            Map<String, Object> today = instanceMapper.selectTodayCount(tenantId);
            if (today != null) {
                overview.put("todayNewCount", toLong(today.get("todayNewCount")));
                overview.put("todayCompletedCount", toLong(today.get("todayCompletedCount")));
            } else {
                overview.put("todayNewCount", 0L);
                overview.put("todayCompletedCount", 0L);
            }
        } catch (Exception e) {
            log.warn("[Dashboard] 今日计数查询失败: {}", e.getMessage());
            overview.put("todayNewCount", 0L);
            overview.put("todayCompletedCount", 0L);
        }

        try {
            overview.put("pendingTaskCount", taskService.countPending(tenantId));
        } catch (Exception e) {
            log.warn("[Dashboard] 待办任务计数失败: {}", e.getMessage());
            overview.put("pendingTaskCount", 0L);
        }

        try {
            overview.put("overdueTaskCount", taskService.countOverdue(null, tenantId));
        } catch (Exception e) {
            log.warn("[Dashboard] 超期任务计数失败: {}", e.getMessage());
            overview.put("overdueTaskCount", 0L);
        }
        return overview;
    }

    /**
     * P2-7: 构建实例趋势数据（复用 monitorInstanceTrend 逻辑，供 dashboard 聚合调用）。
     *
     * @param tenantId 租户 ID
     * @param days     统计天数
     * @return 趋势列表
     */
    private List<Map<String, Object>> buildInstanceTrend(String tenantId, int days) {
        int effectiveDays = (days == 30) ? 30 : 7;
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(effectiveDays - 1L);
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = today.atTime(23, 59, 59);

        List<Map<String, Object>> newCounts = instanceMapper.selectDailyNewCount(tenantId, startDt, endDt);
        List<Map<String, Object>> completedCounts = instanceMapper.selectDailyCompletedCount(tenantId, startDt, endDt);

        Map<String, long[]> byDate = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            byDate.put(start.plusDays(i).toString(), new long[]{0, 0});
        }
        if (newCounts != null) {
            for (Map<String, Object> row : newCounts) {
                String dateStr = String.valueOf(row.get("date"));
                if (byDate.containsKey(dateStr)) {
                    byDate.get(dateStr)[0] = toLong(row.get("newCount"));
                }
            }
        }
        if (completedCounts != null) {
            for (Map<String, Object> row : completedCounts) {
                String dateStr = String.valueOf(row.get("date"));
                if (byDate.containsKey(dateStr)) {
                    byDate.get(dateStr)[1] = toLong(row.get("completedCount"));
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(effectiveDays);
        for (Map.Entry<String, long[]> entry : byDate.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey());
            row.put("newCount", entry.getValue()[0]);
            row.put("completedCount", entry.getValue()[1]);
            result.add(row);
        }
        return result;
    }

    /**
     * P2-4: 解析日期时间字符串（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd）。
     *
     * @param str 日期时间字符串
     * @return 解析后的 LocalDateTime，解析失败返回 null
     */
    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
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

    /**
     * 安全转换为 long，null 或非数字返回 0。
     *
     * @param val 原始值
     * @return long 值
     */
    private long toLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
