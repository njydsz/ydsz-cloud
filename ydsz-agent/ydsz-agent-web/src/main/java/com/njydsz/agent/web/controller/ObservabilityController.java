package com.njydsz.agent.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.server.observability.ObservabilityDashboardService;
import com.njydsz.agent.server.observability.ObservabilityDashboardService.DashboardOverviewDTO;
import com.njydsz.agent.server.observability.ObservabilityDashboardService.ModelUsageDTO;
import com.njydsz.common.core.response.YdszResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 可观测性面板 REST API Controller
 *
 * <p>提供面板数据的查询接口，供前端 Dashboard 渲染：
 *
 * <ul>
 *   <li>{@code GET /api/v1/agent/observability/overview} - 面板概览（今日成本、活跃会话等）
 *   <li>{@code GET /api/v1/agent/observability/model-usage} - 模型使用分布
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/observability")
public class ObservabilityController {

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
    int safeDays = Math.min(Math.max(days, 1), 30);
    log.info("[Observability-API] 查询模型分布: days={}", safeDays);
    return YdszResponse.success(dashboardService.getModelUsageDistribution(safeDays));
  }
}
