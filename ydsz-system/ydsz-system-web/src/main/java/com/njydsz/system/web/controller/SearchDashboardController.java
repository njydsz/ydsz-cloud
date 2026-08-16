package com.njydsz.system.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.analytics.SearchAnalyticsService.HotKeyword;
import com.njydsz.common.search.analytics.SearchAnalyticsService.SearchAnalyticsSummary;
import com.njydsz.common.search.analytics.SearchQualityTracker;
import com.njydsz.common.search.analytics.SearchQualityTracker.QualityReport;

/**
 * 搜索运维看板 Controller
 *
 * <p>为管理后台提供搜索运营数据可视化能力，包括搜索分析与搜索质量追踪：
 *
 * <ul>
 *   <li>搜索分析 — 热门词、零结果词、每日搜索量、汇总数据
 *   <li>搜索质量 — MRR（平均倒数排名）、CTR（点击率）、零结果率、平均延迟
 * </ul>
 *
 * <p><b>接口路径：</b>
 *
 * <pre>
 *   GET  /api/v1/search/dashboard/analytics/summary — 搜索分析汇总
 *   GET  /api/v1/search/dashboard/analytics/hot       — 热搜关键词排行
 *   GET  /api/v1/search/dashboard/analytics/zero      — 零结果关键词排行
 *   GET  /api/v1/search/dashboard/analytics/daily     — 每日搜索量
 *   GET  /api/v1/search/dashboard/quality/report      — 搜索质量报告
 *   DELETE /api/v1/search/dashboard/analytics        — 清空分析数据（运维）
 * </pre>
 *
 * <p><b>权限要求：</b>{@link PermissionCodes#SYSTEM_SEARCH_DASHBOARD}
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SearchAnalyticsService 搜索分析服务
 * @see SearchQualityTracker 搜索质量追踪器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search/dashboard")
@RequiredArgsConstructor
@Tag(name = "搜索运维看板", description = "搜索分析 + 质量追踪数据可视化")
public class SearchDashboardController {

  private final SearchAnalyticsService analyticsService;
  private final SearchQualityTracker qualityTracker;

  /**
   * 获取搜索分析汇总。
   *
   * <p>汇总数据包括：总搜索量、零结果量、零结果率、去重关键词数与零结果关键词数。
   *
   * @return 搜索分析汇总数据
   */
  @GetMapping("/analytics/summary")
  @Operation(summary = "搜索分析汇总", description = "总搜索量、零结果率、去重关键词数等汇总指标")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  public BaseResponse<SearchAnalyticsSummary> getAnalyticsSummary() {
    return BaseResponse.success(analyticsService.getSummary());
  }

  /**
   * 获取热搜关键词排行。
   *
   * @param limit 返回条数上限（默认 20）
   * @return 按搜索次数降序的热搜词列表
   */
  @GetMapping("/analytics/hot")
  @Operation(summary = "热搜关键词排行", description = "按搜索次数降序的热门搜索词列表")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  public BaseResponse<List<HotKeyword>> getHotKeywords(
      @RequestParam(defaultValue = "20") int limit) {
    return BaseResponse.success(analyticsService.getHotKeywords(limit));
  }

  /**
   * 获取零结果关键词排行。
   *
   * <p>反映用户搜索意图与内容供给的缺口，可用于针对性补充内容或优化同义词词典。
   *
   * @param limit 返回条数上限（默认 20）
   * @return 按零结果次数降序的关键词列表
   */
  @GetMapping("/analytics/zero")
  @Operation(summary = "零结果关键词排行", description = "按零结果次数降序的关键词列表，反映内容缺口")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  public BaseResponse<List<HotKeyword>> getZeroResultKeywords(
      @RequestParam(defaultValue = "20") int limit) {
    return BaseResponse.success(analyticsService.getZeroResultKeywords(limit));
  }

  /**
   * 获取近 N 天的每日搜索量。
   *
   * @param days 统计天数范围（默认 30）
   * @return 日期 → 搜索次数的映射
   */
  @GetMapping("/analytics/daily")
  @Operation(summary = "每日搜索量", description = "近 N 天的每日搜索量趋势")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  public BaseResponse<Map<java.time.LocalDate, Long>> getDailySearches(
      @RequestParam(defaultValue = "30") int days) {
    return BaseResponse.success(analyticsService.getDailySearches(days));
  }

  /**
   * 获取搜索质量报告。
   *
   * <p>搜索质量指标包括：
   *
   * <ul>
   *   <li>MRR（Mean Reciprocal Rank）— 用户点击结果的倒数排名的平均值
   *   <li>CTR（Click-Through Rate）— 点击率
   *   <li>Zero Result Rate — 零结果率
   *   <li>Avg Latency — 平均搜索延迟（毫秒）
   * </ul>
   *
   * @return 搜索质量报告
   */
  @GetMapping("/quality/report")
  @Operation(summary = "搜索质量报告", description = "MRR / CTR / 零结果率 / 平均延迟等质量指标")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  public BaseResponse<QualityReport> getQualityReport() {
    return BaseResponse.success(qualityTracker.getReport());
  }

  /**
   * 清空全部搜索分析数据。
   *
   * <p><b>不可逆操作</b>：删除 Redis 中的热门词、零结果词、每日量 key，并清空内存统计。 仅应由运维接口或测试用例调用。
   *
   * @param userId 操作用户 ID（来自请求头 {@code X-User-Id}，记入审计日志）
   * @return 空响应
   */
  @DeleteMapping("/analytics")
  @Operation(summary = "清空搜索分析数据", description = "不可逆操作：删除全部搜索分析数据")
  @Audit(action = AuditAction.DELETE, module = "SYSTEM", content = "清空搜索分析数据")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  public BaseResponse<Void> clearAnalyticsData(
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId) {
    analyticsService.clear();
    log.info("[SearchDashboard] 搜索分析数据已清空, userId={}", userId);
    return BaseResponse.success();
  }
}
