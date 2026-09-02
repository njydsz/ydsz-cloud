package com.njydsz.literule.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.literule.domain.vo.RuleDashboardDistributionVO;
import com.njydsz.literule.domain.vo.RuleDashboardOverviewVO;
import com.njydsz.literule.domain.vo.RuleDashboardRealtimeVO;
import com.njydsz.literule.domain.vo.RuleDashboardTopRuleVO;
import com.njydsz.literule.domain.vo.RuleDashboardTrendVO;
import com.njydsz.literule.server.core.RuleMetrics;
import com.njydsz.literule.server.spi.DashboardDataProvider;

/**
 * 规则引擎监控大盘 Controller
 *
 * <p>P1-6：提供规则引擎监控大盘的 REST API，包含概览 / 趋势 / 分布 / Top 规则 / 实时指标 5 类端点。 路径前缀 {@code
 * /rule-engine/dashboard}。
 *
 * <p>通过 {@link DashboardDataProvider} SPI 反转依赖，由 project 模块提供数据聚合实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/dashboard")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则引擎监控大盘", description = "P1-6 规则引擎指标聚合 API：概览 / 趋势 / 分布 / Top 规则 / 实时指标")
public class RuleDashboardController {

  /** 规则引擎看板数据提供者（由消费方实现） */
  private final DashboardDataProvider dashboardService;

  /** 引擎内建指标（可选，E3 慢规则/热点规则看板数据源） */
  private final ObjectProvider<RuleMetrics> ruleMetricsProvider;

  /**
   * 概览指标
   *
   * <p>统计窗口：今日 0:00 ~ 当前时间。返回规则数量、触发率、P99 耗时、错误率等首屏卡片指标。
   *
   * @return 概览指标
   */
  @GetMapping("/overview")
  @Operation(summary = "概览指标", description = "规则数量、触发率、P99 耗时、错误率等首屏卡片指标")
  public YdszResponse<RuleDashboardOverviewVO> overview() {
    return YdszResponse.success(dashboardService.getOverview());
  }

  /**
   * 趋势指标
   *
   * @param timeRange 时间范围：24h / 7d / 30d（默认 24h）
   * @return 趋势数据（时间序列）
   */
  @GetMapping("/trends")
  @Operation(summary = "趋势指标", description = "按时间维度（小时/天）展示触发次数、P99 耗时、错误率趋势")
  public YdszResponse<RuleDashboardTrendVO> trends(
      @RequestParam(value = "timeRange", defaultValue = "24h") String timeRange) {
    return YdszResponse.success(dashboardService.getTrends(timeRange));
  }

  /**
   * 分布指标
   *
   * @return 分布数据（饼图）
   */
  @GetMapping("/distribution")
  @Operation(summary = "分布指标", description = "按状态/类别/严重度/场景/租户/责任人分组的规则分布")
  public YdszResponse<RuleDashboardDistributionVO> distribution() {
    return YdszResponse.success(dashboardService.getDistribution());
  }

  /**
   * Top 规则列表
   *
   * @param type 排序类型：triggered（最活跃）/ slowest（最慢）/ errorRate（错误率最高）
   * @param limit 返回条数（默认 10，最大 50）
   * @return Top 规则列表
   */
  @GetMapping("/top-rules")
  @Operation(summary = "Top 规则列表", description = "按触发次数/平均耗时/错误率排序的 Top 规则")
  public YdszResponse<List<RuleDashboardTopRuleVO>> topRules(
      @RequestParam(value = "type", defaultValue = "triggered") String type,
      @RequestParam(value = "limit", defaultValue = "10") @Min(1) @Max(50) int limit) {
    return YdszResponse.success(dashboardService.getTopRules(type, limit));
  }

  /**
   * 实时指标
   *
   * @return 实时指标（当前 QPS、活跃规则数）
   */
  @GetMapping("/realtime")
  @Operation(summary = "实时指标", description = "当前 QPS、活跃规则数、注册规则数等秒级实时指标")
  public YdszResponse<RuleDashboardRealtimeVO> realtime() {
    return YdszResponse.success(dashboardService.getRealtime());
  }

  /**
   * 慢规则 Top N（E3 看板）
   *
   * <p>基于引擎内建指标（{@code InMemoryRuleMetrics}）按平均耗时倒序返回规则级统计。 Micrometer 场景返回空列表（建议 Grafana 查询）。
   *
   * @param limit 返回条数（默认 10，最大 50）
   * @return 慢规则统计列表
   */
  @GetMapping("/slow-rules")
  @Operation(summary = "慢规则 Top N", description = "按平均耗时倒序的规则级耗时统计（E3）")
  public YdszResponse<List<com.njydsz.literule.server.core.RuleMetrics.RuleStatSnapshot>> slowRules(
      @RequestParam(value = "limit", defaultValue = "10") @Min(1) @Max(50) int limit) {
    RuleMetrics metrics = ruleMetricsProvider.getIfAvailable();
    if (metrics == null) {
      return YdszResponse.success(List.of());
    }
    return YdszResponse.success(metrics.getSlowRuleStats(limit));
  }

  /**
   * 热点规则 Top N（E3 看板）
   *
   * <p>基于引擎内建指标按评估次数倒序返回规则级统计。
   *
   * @param limit 返回条数（默认 10，最大 50）
   * @return 热点规则统计列表
   */
  @GetMapping("/hot-rules")
  @Operation(summary = "热点规则 Top N", description = "按评估次数倒序的规则级热度统计（E3）")
  public YdszResponse<List<com.njydsz.literule.server.core.RuleMetrics.RuleStatSnapshot>> hotRules(
      @RequestParam(value = "limit", defaultValue = "10") @Min(1) @Max(50) int limit) {
    RuleMetrics metrics = ruleMetricsProvider.getIfAvailable();
    if (metrics == null) {
      return YdszResponse.success(List.of());
    }
    return YdszResponse.success(metrics.getHotRuleStats(limit));
  }
}

