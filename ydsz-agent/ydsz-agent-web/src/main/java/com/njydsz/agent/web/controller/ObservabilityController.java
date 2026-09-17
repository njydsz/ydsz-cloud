package com.njydsz.agent.web.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.trace.TraceContextHolder;
import com.njydsz.agent.server.observability.ObservabilityDashboardService;
import com.njydsz.agent.server.observability.ObservabilityDashboardService.DashboardOverviewDTO;
import com.njydsz.agent.server.observability.ObservabilityDashboardService.ModelUsageDTO;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;

/**
 * Agent 可观测性面板 REST API Controller
 *
 * <p>提供面板数据的查询接口，供前端 Dashboard 渲染：
 *
 * <ul>
 *   <li>{@code GET /agent/observability/overview} - 面板概览（今日成本、活跃会话等）
 *   <li>{@code GET /agent/observability/model-usage} - 模型使用分布
 *   <li>{@code POST /agent/observability/trace-context} - 查询当前链路上下文（调试用）
 *   <li>{@code GET /agent/observability/metrics/bot/{botId}} - 按 botId 聚合指标
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@ApiVersion("26.09.17")
@RestController
@RequestMapping("/agent/observability")
public class ObservabilityController {

  /** 统计天数上限 */
  private static final int MAX_QUERY_DAYS = 30;

  /** 按 botId 查询时默认统计天数 */
  private static final int DEFAULT_BOT_METRICS_DAYS = 7;

  /** trace-context 返回 Map 初始容量（botId / turnId / conversationId / accountId 四个键） */
  private static final int TRACE_CONTEXT_MAP_CAPACITY = 4;

  /** 按 botId 指标查询返回占位 Map 初始容量 */
  private static final int BOT_METRICS_MAP_CAPACITY = 4;

  private final ObservabilityDashboardService dashboardService;

  public ObservabilityController(ObservabilityDashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /**
   * 获取面板概览数据。
   *
   * <p>返回今日成本、活跃会话数、模型用量分布等聚合数据， 用于面板顶部卡片和概览图表。
   *
   * @return 统一响应结果，data 为 {@link DashboardOverviewDTO}
   */
  @GetMapping("/overview")
  public YdszResponse<DashboardOverviewDTO> getOverview() {
    log.info("[Observability-API] 查询面板概览");
    return YdszResponse.success(dashboardService.getOverview());
  }

  /**
   * 获取模型使用分布。
   *
   * <p>返回最近 N 天内各模型的 Token 用量与成本分布， 用于面板的模型占比饼图。
   *
   * @param days 统计天数（默认 7，最大 30）
   * @return 统一响应结果，data 为 {@link ModelUsageDTO} 列表
   */
  @GetMapping("/model-usage")
  public YdszResponse<List<ModelUsageDTO>> getModelUsage(
      @RequestParam(defaultValue = "7") int days) {
    int safeDays = Math.min(Math.max(days, 1), MAX_QUERY_DAYS);
    log.info("[Observability-API] 查询模型分布: days={}", safeDays);
    return YdszResponse.success(dashboardService.getModelUsageDistribution(safeDays));
  }

  /**
   * 查询当前线程的链路上下文（调试用）。
   *
   * <p>返回当前请求线程通过 {@link TraceContextHolder} 设置的业务关联维度信息，
   * 用于排查链路追踪中业务属性未生效的问题。
   *
   * @return 统一响应结果，data 为链路上下文字典（含 botId、turnId、conversationId、accountId）
   */
  @PostMapping("/trace-context")
  public YdszResponse<Map<String, String>> getTraceContext() {
    log.info("[Observability-API] 查询当前链路上下文");
    Map<String, String> context = new HashMap<>(TRACE_CONTEXT_MAP_CAPACITY);
    TraceContextHolder.TraceContext ctx = TraceContextHolder.get();
    if (ctx != null) {
      context.put("botId", ctx.botId() != null ? ctx.botId() : "");
      context.put("turnId", ctx.turnId() != null ? ctx.turnId() : "");
      context.put("conversationId", ctx.conversationId() != null ? ctx.conversationId() : "");
      context.put("accountId", ctx.accountId() != null ? ctx.accountId() : "");
    } else {
      context.put("botId", "");
      context.put("turnId", "");
      context.put("conversationId", "");
      context.put("accountId", "");
    }
    return YdszResponse.success(context);
  }

  /**
   * 按 botId 聚合查询指标数据。
   *
   * <p>返回指定 Agent 定义在最近 N 天内的 LLM 调用次数、Token 消耗、成本等聚合指标，
   * 用于在面板中按 Agent 维度展示用量分析。
   *
   * @param botId Agent 定义 ID
   * @param days 统计天数（默认 7，最大 30）
   * @return 统一响应结果，data 为指标数据 Map
   */
  @GetMapping("/metrics/bot/{botId}")
  public YdszResponse<Map<String, Object>> getMetricsByBotId(
      @PathVariable String botId,
      @RequestParam(defaultValue = "7") int days) {
    int safeDays = Math.min(Math.max(days, 1), MAX_QUERY_DAYS);
    log.info("[Observability-API] 按 botId 查询指标: botId={}, days={}", botId, safeDays);
    // TODO: 由 dashboardService 提供按 botId 聚合的指标查询能力
    // 当前返回占位结构，待 ObservabilityDashboardService 扩展后填充实际数据
    Map<String, Object> result = new HashMap<>(BOT_METRICS_MAP_CAPACITY);
    result.put("botId", botId);
    result.put("days", safeDays);
    result.put("placeholder", "待实现按 botId 聚合查询");
    return YdszResponse.success(result);
  }
}
