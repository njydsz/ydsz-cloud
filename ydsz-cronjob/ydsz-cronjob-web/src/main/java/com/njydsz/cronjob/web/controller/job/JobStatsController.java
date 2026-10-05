package com.njydsz.cronjob.web.controller.job;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.cronjob.domain.service.JobStatsQueryService;
import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;

/**
 * 任务执行统计 Controller（P2-3 执行历史趋势可视化 + P1-2 监控仪表盘）。
 *
 * <p>提供任务执行统计的多维度查询接口，供前端可视化展示：
 *
 * <ul>
 *   <li>趋势图：每日统计（{@link #daily}）
 *   <li>汇总卡：日期范围汇总（{@link #summary}）
 *   <li>仪表盘：全局运行状态（{@link #dashboard}）
 *   <li>失败列表：最近失败任务 Top N（{@link #recentFailures}）
 *   <li>热力图：24 小时执行分布（{@link #heatmap}）
 * </ul>
 *
 * <p>数据源：{@code ydsz_job_daily_stats}（每日聚合表，由 {@code DailyStatsAggregator} 周期性生成） + {@code
 * ydsz_job_log}（原始日志，用于实时统计）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Tag(name = "任务执行统计", description = "每日趋势、范围汇总、仪表盘、热力图、失败列表")
@Slf4j
@ApiVersion("26.10.01")
@RestController
@RequestMapping("/cronjob/stats")
@RequiredArgsConstructor
public class JobStatsController {
  /** 任务统计查询 Service（DDD 分层：Controller → Service → Repository） */
  private final JobStatsQueryService jobStatsQueryService;

  /**
   * 查询指定任务的每日统计（趋势图数据源）。
   *
   * <p>按日期升序返回每日统计数据（触发/成功/失败/超时/平均耗时），是折线图/柱状图的标准数据源。 数据由 {@code DailyStatsAggregator} 在每日 0
   * 点批量生成（滞后一天）。
   *
   * @param jobId 任务 ID
   * @param startDate 起始日期（含，格式 yyyy-MM-dd）
   * @param endDate 结束日期（含，格式 yyyy-MM-dd）
   * @return 每日统计列表（按日期升序）
   */
  @Operation(summary = "查询每日执行统计趋势")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping("/daily")
  public YdszResponse<List<JobDailyStatsVO>> daily(
      @RequestParam String jobId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    // 通过 Service 查询每日统计（Service → Repository）
    return YdszResponse.success(
        jobStatsQueryService.getDailyStats(jobId, startDate, endDate));
  }

  /**
   * 查询指定任务在日期范围内的汇总统计。
   *
   * <p>对日期范围内的每日统计做累加（avgDurationMs 用算术平均），得到区间汇总：
   *
   * <ul>
   *   <li>总触发次数 / 总成功次数 / 总失败次数 / 总超时次数
   *   <li>平均耗时（毫秒，所有日期的算术平均）
   * </ul>
   *
   * @param jobId 任务 ID
   * @param startDate 起始日期（含）
   * @param endDate 结束日期（含）
   * @return 汇总统计 Map
   */
  @Operation(summary = "查询执行统计汇总")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping("/summary")
  public YdszResponse<Map<String, Object>> summary(
      @RequestParam String jobId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    // 通过 Service 汇总统计（Service → Repository）
    return YdszResponse.success(jobStatsQueryService.getSummary(jobId, startDate, endDate));
  }

  // ==================== P1-2: 运维监控仪表盘增强 ====================

  /**
   * P1-2: 获取全局监控仪表盘数据。
   *
   * <p>返回调度引擎的整体运行状态概览，包括：
   *
   * <ul>
   *   <li>任务总数/正常运行/已暂停/异常
   *   <li>今日执行统计（触发/成功/失败/成功率）
   *   <li>当前运行中任务数
   *   <li>系统负载评分
   *   <li>最近失败任务列表（Top 10）
   * </ul>
   *
   * @return 仪表盘数据
   */
  @Operation(summary = "全局监控仪表盘")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping("/dashboard")
  public YdszResponse<Map<String, Object>> dashboard() {
    // 通过 Service 获取仪表盘数据（Service → Repository）
    return YdszResponse.success(jobStatsQueryService.getDashboard());
  }

  /**
   * P1-2: 获取最近失败任务列表。
   *
   * <p>按 start_time 倒序返回最近 FAILED 状态的执行日志。limit 默认 10，最大 100。 配合前端"故障快速定位"面板使用。
   *
   * @param limit 返回条数（默认 10，最大 100）
   * @return 失败日志列表
   */
  @Operation(summary = "最近失败任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping("/recent-failures")
  public YdszResponse<List<JobLogVO>> recentFailures(@RequestParam(defaultValue = "10") int limit) {
    // 通过 Service 查询最近失败日志（Service → Repository）
    return YdszResponse.success(jobStatsQueryService.getRecentFailures(limit));
  }

  /**
   * P1-2: 获取任务执行热力图数据。
   *
   * <p>按小时聚合统计任务执行分布（0-23 共 24 个时段），用于识别业务高峰时段。 缺省查询当天。
   *
   * @param date 查询日期（默认今天）
   * @return 24 小时执行分布 [{hour, count}, ...]
   */
  @Operation(summary = "执行热力图（按小时分布）")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping("/heatmap")
  public YdszResponse<List<Map<String, Object>>> heatmap(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    // 通过 Service 获取热力图数据（Service → Repository）
    return YdszResponse.success(jobStatsQueryService.getHeatmap(date));
  }
}
