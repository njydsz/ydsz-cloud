package com.njydsz.workflow.web.controller;

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
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.domain.vo.FlowAnomalyVO;
import com.njydsz.workflow.domain.vo.FlowApproverEfficiencyVO;
import com.njydsz.workflow.domain.vo.FlowBottleneckVO;
import com.njydsz.workflow.domain.vo.FlowEfficiencyStatsVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowTrendVO;
import com.njydsz.workflow.server.service.FlowEfficiencyService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 监控看板与效率分析统一 Controller
 *
 * <p>业务背景：工作流「运营管理」HTTP 入口，提供「监控中心」与「效率分析」能力。
 * 为运维人员提供流程实例的实时状态监控、异常流程识别、趋势分析、类型分布、超期任务排行、审批人负载分布、
 * 流程效率对比、首屏仪表盘聚合、节点瓶颈排名、健康度评分等能力。
 *
 * <p><b>权限模型：</b>所有接口通过 {@link AuthApiPermission} 校验 {@link PermissionCodes#WORKFLOW_MONITOR_VIEW}
 * 权限码；只读、不写。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传、日期范围解析、VO 转换； 监控指标计算下沉到 {@link FlowEfficiencyService} / {@link
 * FlowInstanceService} / {@link FlowTaskService}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowEfficiencyService 效率分析服务
 * @see FlowInstanceService 流程实例服务
 * @see FlowTaskService 任务服务
 */
@Slf4j
@RestController
@Tag(name = "workflow-monitor", description = "工作流监控看板与效率分析统一接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowMonitorDashboardController {

    /** 卡单检测阈值（小时） */
  private static final int STUCK_HOURS_THRESHOLD = 24;

  /** 长运行检测阈值（天） */
  private static final int LONG_RUNNING_DAYS = 7;

  /** 列表初始容量（32） */
  private static final int LIST_INIT_CAPACITY_32 = 32;

  /** 趋势统计默认天数 */
  private static final int TREND_DAYS = 7;

  /** 超期任务 TOP N */
  private static final int OVERDUE_TOP_N = 5;

  /** 异常检测样本 TOP N */
  private static final int ANOMALY_TOP_N = 5;

  /** 每天的小时数 */
  private static final int HOURS_PER_DAY = 24;

  /** 严重异常天数阈值（RED） */
  private static final int ANOMALY_DAYS_SEVERE = 7;

  /** 一般异常天数阈值（YELLOW） */
  private static final int ANOMALY_DAYS_NORMAL = 3;

  /** 月度统计天数 */
  private static final int MONTH_STAT_DAYS = 30;

  /** 默认统计天数（近 7 天） */
  private static final int DEFAULT_STAT_DAYS = 7;

  /** 当日结束时间：时（23） */
  private static final int END_OF_DAY_HOUR = 23;

  /** 当日结束时间：分/秒（59） */
  private static final int END_OF_DAY_MIN_SEC = 59;

  /** GAP-P2: 审批效率分析服务（异常检测 / 效率统计 / 健康度评分） */
  private final FlowEfficiencyService efficiencyService;

  /** 任务服务（监控概览待办/超期计数） */
  private final FlowTaskService taskService;

  /** 流程实例服务（VO 查询 / 异常实例详情查询） */
  private final FlowInstanceService instanceService;

  // ============== P0-3 / P2-4: 监控看板聚合端点 ==============

  /**
   * P0-3 / P2-4: 监控概览 — 聚合实例/任务/效率统计。
   *
   * @return 概览统计数据：runningCount/todayNewCount/pendingTaskCount/overdueTaskCount/todayCompletedCount
   */
  @Operation(summary = "监控概览")
  @GetMapping("/monitor/overview")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  public YdszResponse<Map<String, Object>> monitorOverview() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(buildOverview(tenantId));
  }

  /**
   * P0-3 / P2-4: 异常流程列表 — 超期/卡单/长期运行/高驳回率。
   *
   * @param anomalyType 异常类型过滤（TIMEOUT/STUCK/REPEATED_REJECT/CIRCULAR_APPROVAL，可空）
   * @param warnLevel 预警级别过滤（RED/YELLOW/ORANGE，可空）
   * @param pageNum 页码（从 1 开始）
   * @param pageSize 每页大小
   * @return 分页异常实例列表
   */
  @Operation(summary = "异常流程列表")
  @GetMapping("/monitor/anomaly")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  public YdszResponse<List<Map<String, Object>>> monitorAnomaly(
      @RequestParam(required = false) String anomalyType,
      @RequestParam(required = false) String warnLevel,
      @RequestParam(defaultValue = "1") @Min(1) int pageNum,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();

    List<Map<String, Object>> all = new ArrayList<>(100);
    try {
      List<FlowAnomalyVO> detected =
          efficiencyService.detectAnomalies(
              tenantId, 100, STUCK_HOURS_THRESHOLD, LONG_RUNNING_DAYS);
      if (detected != null) {
        for (FlowAnomalyVO a : detected) {
          Map<String, Object> item = mapAnomaly(a);
          if (item == null) {
            continue;
          }
          if (anomalyType != null
              && !anomalyType.isBlank()
              && !anomalyType.equals(item.get("anomalyType"))) {
            continue;
          }
          if (warnLevel != null
              && !warnLevel.isBlank()
              && !warnLevel.equals(item.get("warnLevel"))) {
            continue;
          }
          all.add(item);
        }
      }
    } catch (Exception e) {
      log.warn("[Monitor] 异常检测失败: {}", e.getMessage());
    }

    int total = all.size();
    int from = Math.min((pageNum - 1) * pageSize, total);
    int to = Math.min(from + pageSize, total);
    List<Map<String, Object>> page = from < to ? all.subList(from, to) : new ArrayList<>(0);

    return PageResponse.success((long) total, (long) pageNum, (long) pageSize, page);
  }

  /**
   * P0-3 / P2-4: 实例趋势 — 按日期统计新增/完成数。
   *
   * @param days 统计天数（默认 7，可选 30）
   * @return 趋势列表
   */
  @Operation(summary = "实例趋势")
  @GetMapping("/monitor/instanceTrend")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  public YdszResponse<List<Map<String, Object>>> monitorInstanceTrend(
      @RequestParam(defaultValue = "7") int days) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(buildInstanceTrend(tenantId, days));
  }

  /**
   * P0-3 / P2-4: 审批人效率排名 — SQL GROUP BY 聚合。
   *
   * @param topN 返回条数上限
   * @param startTime finish_at 下界（可空）
   * @param endTime finish_at 上界（可空）
   * @return 审批人排名列表
   */
  @Operation(summary = "审批人效率排名")
  @GetMapping("/monitor/approverEfficiency")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  public YdszResponse<List<Map<String, Object>>> monitorApproverEfficiency(
      @RequestParam(defaultValue = "10") int topN,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    LocalDateTime startDt = parseDateTime(startTime);
    LocalDateTime endDt = parseDateTime(endTime);
    List<Map<String, Object>> rows =
        efficiencyService.selectApproverEfficiency(tenantId, startDt, endDt, topN);

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
    return YdszResponse.success(result);
  }

  /**
   * P0-3 / P2-4: 流程类型分布 — SQL GROUP BY 聚合。
   *
   * @param startTime start_at 下界（可空）
   * @param endTime start_at 上界（可空）
   * @return 分布列表
   */
  @Operation(summary = "流程类型分布")
  @GetMapping("/monitor/flowTypeDistribution")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  public YdszResponse<List<Map<String, Object>>> monitorFlowTypeDistribution(
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    LocalDateTime startDt = parseDateTime(startTime);
    LocalDateTime endDt = parseDateTime(endTime);
    List<Map<String, Object>> rows =
        instanceService.selectFlowTypeDistribution(tenantId, startDt, endDt);

    long total = 0;
    if (rows != null) {
      for (Map<String, Object> row : rows) {
        total += toLong(row.get("cnt"));
      }
    }

    List<Map<String, Object>> result = new ArrayList<>(LIST_INIT_CAPACITY_32);
    if (rows != null) {
      for (Map<String, Object> row : rows) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("flowCode", row.get("flowCode"));
        item.put(
            "flowName", row.get("flowName") == null ? row.get("flowCode") : row.get("flowName"));
        long cnt = toLong(row.get("cnt"));
        item.put("count", cnt);
        item.put("percentage", total > 0 ? Math.round(cnt * 10000.0 / total) / 100.0 : 0.0);
        result.add(item);
      }
    }
    return YdszResponse.success(result);
  }

  // ============== P2-7: 监控仪表盘 UI 增强 ==============

  /**
   * P2-7: 仪表盘聚合端点 — 一次性返回概览 + 7 天趋势 + 异常 Top5 + 效率统计。
   *
   * @return 仪表盘聚合数据
   */
  @GetMapping("/monitor/dashboard")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  @Operation(summary = "监控仪表盘聚合数据（首屏一次加载）")
  public YdszResponse<Map<String, Object>> monitorDashboard() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    Map<String, Object> dashboard = new LinkedHashMap<>(16);

    try {
      dashboard.put("overview", buildOverview(tenantId));
    } catch (Exception e) {
      log.warn("[Dashboard] overview 聚合失败: {}", e.getMessage());
      dashboard.put("overview", new LinkedHashMap<>(16));
    }

    try {
      dashboard.put("instanceTrend", buildInstanceTrend(tenantId, TREND_DAYS));
    } catch (Exception e) {
      log.warn("[Dashboard] instanceTrend 聚合失败: {}", e.getMessage());
      dashboard.put("instanceTrend", new ArrayList<>(0));
    }

    try {
      List<Map<String, Object>> overdueTop = taskService.selectOverdueTopN(tenantId, OVERDUE_TOP_N);
      dashboard.put("overdueTop5", overdueTop != null ? overdueTop : new ArrayList<>(0));
    } catch (Exception e) {
      log.warn("[Dashboard] overdueTop5 查询失败: {}", e.getMessage());
      dashboard.put("overdueTop5", new ArrayList<>(0));
    }

    try {
      List<FlowAnomalyVO> anomalies =
          efficiencyService.detectAnomalies(
              tenantId, ANOMALY_TOP_N, STUCK_HOURS_THRESHOLD, LONG_RUNNING_DAYS);
      dashboard.put("anomalyTop5", anomalies != null ? anomalies : new ArrayList<>(0));
    } catch (Exception e) {
      log.warn("[Dashboard] anomalyTop5 查询失败: {}", e.getMessage());
      dashboard.put("anomalyTop5", new ArrayList<>(0));
    }

    try {
      dashboard.put("efficiency", efficiencyService.efficiencyStats(tenantId, null, null));
    } catch (Exception e) {
      log.warn("[Dashboard] efficiency 查询失败: {}", e.getMessage());
      dashboard.put("efficiency", new LinkedHashMap<>());
    }

    try {
      dashboard.put("healthScore", efficiencyService.healthScore(tenantId, null, null));
    } catch (Exception e) {
      log.warn("[Dashboard] healthScore 查询失败: {}", e.getMessage());
      dashboard.put("healthScore", new LinkedHashMap<>());
    }

    return YdszResponse.success(dashboard);
  }

  /**
   * P2-7: 超期任务 Top N 排行
   *
   * @param limit 返回条数上限（默认 10，最大 100）
   * @return 超期任务列表
   */
  @GetMapping("/monitor/overdueTasks")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  @Operation(summary = "超期任务 Top N 排行（按超期时长降序）")
  public YdszResponse<List<Map<String, Object>>> monitorOverdueTasks(
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    List<Map<String, Object>> rows = taskService.selectOverdueTopN(tenantId, limit);
    return YdszResponse.success(rows != null ? rows : new ArrayList<>());
  }

  /**
   * P2-7: 审批人负载分布
   *
   * @param limit 返回条数上限（默认 10，最大 100）
   * @return 审批人负载列表
   */
  @GetMapping("/monitor/approverWorkload")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  @Operation(summary = "审批人负载分布（当前待办数量）")
  public YdszResponse<List<Map<String, Object>>> monitorApproverWorkload(
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    List<Map<String, Object>> rows = taskService.selectWorkloadByAssignee(tenantId, limit);
    return YdszResponse.success(rows != null ? rows : new ArrayList<>());
  }

  /**
   * P2-7: 流程效率对比
   *
   * @param startTime finish_at 下界（可空）
   * @param endTime finish_at 上界（可空）
   * @return 流程效率对比列表
   */
  @GetMapping("/monitor/flowEfficiencyComparison")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  @Operation(summary = "流程效率对比（按流程编码分组）")
  public YdszResponse<List<Map<String, Object>>> monitorFlowEfficiencyComparison(
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    LocalDateTime startDt = parseDateTime(startTime);
    LocalDateTime endDt = parseDateTime(endTime);
    List<Map<String, Object>> rows =
        efficiencyService.selectFlowEfficiencyComparison(tenantId, startDt, endDt);
    return YdszResponse.success(rows != null ? rows : new ArrayList<>());
  }

  // ============== GAP-P2: 审批效率分析 ==============

  /**
   * GAP-P2: 审批效率统计 — 单量/平均耗时/代批率/超期率
   *
   * @param startTime 开始时间（可选）
   * @param endTime 结束时间（可选）
   * @return 统计结果
   */
  @Operation(summary = "审批效率统计")
  @GetMapping("/efficiency/stats")
  public YdszResponse<FlowEfficiencyStatsVO> efficiencyStats(
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(efficiencyService.efficiencyStats(tenantId, startTime, endTime));
  }

  /**
   * GAP-P2: 节点瓶颈排名
   *
   * @param flowCode 流程编码（可选）
   * @param limit 返回条数上限
   * @return 瓶颈节点列表
   */
  @Operation(summary = "节点瓶颈排名")
  @GetMapping("/efficiency/bottleneck")
  public YdszResponse<List<FlowBottleneckVO>> bottleneckRanking(
      @RequestParam(required = false) String flowCode,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(efficiencyService.bottleneckRanking(tenantId, flowCode, limit));
  }

  /**
   * GAP-P2: 审批人效率排名
   *
   * @param startTime 开始时间（可选）
   * @param endTime 结束时间（可选）
   * @param limit 返回条数上限
   * @return 审批人排名列表
   */
  @Operation(summary = "审批人效率排名")
  @GetMapping("/efficiency/approverRanking")
  public YdszResponse<List<FlowApproverEfficiencyVO>> approverRanking(
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(
        efficiencyService.approverRanking(tenantId, startTime, endTime, limit));
  }

  /**
   * GAP-P2: 审批趋势
   *
   * @param interval 聚合粒度：DAY / WEEK / MONTH
   * @param startTime 开始时间（可选）
   * @param endTime 结束时间（可选）
   * @return 趋势列表
   */
  @Operation(summary = "审批趋势")
  @GetMapping("/efficiency/trend")
  public YdszResponse<List<FlowTrendVO>> approvalTrend(
      @RequestParam(defaultValue = "DAY") String interval,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(
        efficiencyService.approvalTrend(tenantId, interval, startTime, endTime));
  }

  /**
   * P1: 流程健康度综合评分
   *
   * @param startTime 开始时间（可空）
   * @param endTime 结束时间（可空）
   * @return 评分结果
   */
  @Operation(summary = "流程健康度综合评分")
  @GetMapping("/efficiency/healthScore")
  public YdszResponse<Map<String, Object>> healthScore(
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(efficiencyService.healthScore(tenantId, startTime, endTime));
  }

  // ============== 私有辅助方法 ==============

  /**
   * 将 efficiencyService 返回的异常 Map 映射为前端 AnomalyInstanceDTO 字段。
   *
   * @param a 原始异常数据
   * @return 映射后的前端 DTO 结构
   */
  private Map<String, Object> mapAnomaly(FlowAnomalyVO a) {
    String type = a.getType() != null ? a.getType() : "UNKNOWN";
    Map<String, Object> item = new LinkedHashMap<>();
    String anomalyType;
    switch (type) {
      case "STUCK" -> anomalyType = "STUCK";
      case "HIGH_REJECTION" -> anomalyType = "REPEATED_REJECT";
      case "LONG_RUNNING", "OVERDUE" -> anomalyType = "TIMEOUT";
      default -> anomalyType = "TIMEOUT";
    }
    item.put("anomalyType", anomalyType);

    String instanceId = a.getInstanceId();
    if (instanceId == null) {
      instanceId = a.getTaskId();
    }
    item.put("id", instanceId == null ? 0 : toLong(instanceId));

    if (instanceId != null) {
      try {
        long idLong = Long.parseLong(instanceId);
        FlowInstanceVO inst = instanceService.getById(String.valueOf(idLong));
        if (inst != null) {
          item.put("flowCode", inst.getFlowCode());
          item.put("flowName", inst.getFlowName());
          item.put("title", inst.getTitle());
          item.put("initiatorName", inst.getInitiatorName());
          item.put("status", inst.getFlowStatus());
          item.put("currentNodeName", inst.getCurrentNodeName());
          item.put("startTime", inst.getStartAt() == null ? null : inst.getStartAt().toString());
        }
      } catch (NumberFormatException e) {
        // 实例查询失败不阻塞，降级使用 detectAnomalies 返回的字段
        log.warn("[FlowMonitor] 实例查询失败，降级使用异常检测字段: {}", e.getMessage());
      }
    }
    item.putIfAbsent("currentNodeName", a.getNodeName());

    Long stuckHours = a.getStuckHours();
    if (stuckHours != null) {
      item.put("overdueDays", stuckHours / HOURS_PER_DAY);
    }

    long days = item.get("overdueDays") instanceof Number d ? d.longValue() : 0;
    String warnLevel;
    if (anomalyType.equals("TIMEOUT")) {
      if (days >= ANOMALY_DAYS_SEVERE) {
        warnLevel = "RED";
      } else if (days >= ANOMALY_DAYS_NORMAL) {
        warnLevel = "YELLOW";
      } else {
        warnLevel = "ORANGE";
      }
    } else {
      warnLevel = "YELLOW";
    }
    item.put("warnLevel", warnLevel);

    item.put("description", a.getDescription());
    return item;
  }

  /**
   * P2-7: 构建监控概览数据
   *
   * @param tenantId 租户 ID
   * @return 概览统计数据 Map
   */
  private Map<String, Object> buildOverview(String tenantId) {
    Map<String, Object> overview = new LinkedHashMap<>();
    long running = 0;
    try {
      List<Map<String, Object>> statusCounts = instanceService.selectCountGroupByStatus(tenantId);
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
      Map<String, Object> today = instanceService.selectTodayCount(tenantId);
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
   * P2-7: 构建实例趋势数据
   *
   * @param tenantId 租户 ID
   * @param days 统计天数
   * @return 趋势列表
   */
  private List<Map<String, Object>> buildInstanceTrend(String tenantId, int days) {
    int effectiveDays = (days == MONTH_STAT_DAYS) ? MONTH_STAT_DAYS : DEFAULT_STAT_DAYS;
    LocalDate today = LocalDate.now();
    LocalDate start = today.minusDays(effectiveDays - 1L);
    LocalDateTime startDt = start.atStartOfDay();
    LocalDateTime endDt = today.atTime(END_OF_DAY_HOUR, END_OF_DAY_MIN_SEC, END_OF_DAY_MIN_SEC);

    List<Map<String, Object>> newCounts =
        instanceService.selectDailyNewCount(tenantId, startDt, endDt);
    List<Map<String, Object>> completedCounts =
        instanceService.selectDailyCompletedCount(tenantId, startDt, endDt);

    Map<String, long[]> byDate = new LinkedHashMap<>();
    for (int i = 0; i < effectiveDays; i++) {
      byDate.put(start.plusDays(i).toString(), new long[] {0, 0});
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
   * P2-4: 解析日期时间字符串
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
