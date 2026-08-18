package com.njydsz.cronjob.web.controller.job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.repository.JobDailyStatsRepository;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

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
 * @since 1.0.0
 */
@Tag(name = "任务执行统计", description = "每日趋势、范围汇总、仪表盘、热力图、失败列表")
@RestController
@RequestMapping("/api/v1/cronjob/stats")
@RequiredArgsConstructor
public class JobStatsController {

  /** 每日统计 Repository（DDD 分层：Controller 通过 Repository 接口访问） */
  private final JobDailyStatsRepository jobDailyStatsRepository;

  /** 日志 Repository（仪表盘实时数据查询） */
  private final JobLogRepository jobLogRepository;

  /** 任务 Repository（任务总数统计） */
  private final JobRepository jobRepository;

  /** Prometheus 指标（可选注入） */
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

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
  public BaseResponse<List<JobDailyStatsVO>> daily(
      @RequestParam String jobId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    // 通过 Repository 查询（LocalDate 重载内部转换为 LocalDateTime）
    return BaseResponse.success(
        jobDailyStatsRepository.findByJobIdAndDateRange(jobId, startDate, endDate));
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
  public BaseResponse<Map<String, Object>> summary(
      @RequestParam String jobId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    // 1. 通过 Repository 查询每日统计 VO 列表
    List<JobDailyStatsVO> list =
        jobDailyStatsRepository.findByJobIdAndDateRange(jobId, startDate, endDate);
    // 2. 累加统计
    long fireCount = 0L;
    long successCount = 0L;
    long failCount = 0L;
    long timeoutCount = 0L;
    long totalDuration = 0L;
    long durationSamples = 0L;
    for (JobDailyStatsVO s : list) {
      if (s.getFireCount() != null) {
        fireCount += s.getFireCount();
      }
      if (s.getSuccessCount() != null) {
        successCount += s.getSuccessCount();
      }
      if (s.getFailCount() != null) {
        failCount += s.getFailCount();
      }
      if (s.getTimeoutCount() != null) {
        timeoutCount += s.getTimeoutCount();
      }
      if (s.getAvgDurationMs() != null) {
        totalDuration += s.getAvgDurationMs();
        durationSamples++;
      }
    }
    Map<String, Object> summary = new HashMap<>();
    summary.put("jobId", jobId);
    summary.put("startDate", startDate);
    summary.put("endDate", endDate);
    summary.put("fireCount", fireCount);
    summary.put("successCount", successCount);
    summary.put("failCount", failCount);
    summary.put("timeoutCount", timeoutCount);
    summary.put("avgDurationMs", durationSamples > 0 ? totalDuration / durationSamples : 0L);
    return BaseResponse.success(summary);
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
  public BaseResponse<Map<String, Object>> dashboard() {
    Map<String, Object> dashboard = new HashMap<>();
    // 1. 任务状态分布（通过 Repository 统计）
    Map<String, Object> taskStats = new HashMap<>();
    taskStats.put("total", jobRepository.countAll());
    taskStats.put("normal", jobRepository.countByStatus("NORMAL"));
    taskStats.put("paused", jobRepository.countByStatus("PAUSED"));
    taskStats.put("error", jobRepository.countByStatus("ERROR"));
    taskStats.put("autoPaused", jobRepository.countByStatus("AUTO_PAUSED"));
    dashboard.put("taskStats", taskStats);

    // 2. 今日执行统计（通过 Repository 统计）
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    Map<String, Object> todayExec = new HashMap<>();
    long todayTotal = jobLogRepository.countByStatusAfter(null, todayStart);
    long todaySuccess = jobLogRepository.countByStatusAfter("SUCCESS", todayStart);
    long todayFailed = jobLogRepository.countByStatusAfter("FAILED", todayStart);
    long todayRunning = jobLogRepository.countByStatusAfter("RUNNING", null);
    todayExec.put("total", todayTotal);
    todayExec.put("success", todaySuccess);
    todayExec.put("failed", todayFailed);
    todayExec.put("running", todayRunning);
    todayExec.put(
        "successRate",
        todayTotal > 0
            ? String.format("%.1f%%", todaySuccess * 100.0 / todayTotal)
            : "N/A");
    dashboard.put("todayExec", todayExec);

    // 3. Prometheus 指标
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics != null) {
      Map<String, Object> systemMetrics = new HashMap<>();
      systemMetrics.put("running", todayRunning);
      dashboard.put("systemMetrics", systemMetrics);
    }

    return BaseResponse.success(dashboard);
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
  public BaseResponse<List<JobLogVO>> recentFailures(@RequestParam(defaultValue = "10") int limit) {
    // 通过 Repository 查询最近失败日志（Repository 内部处理 LIMIT 上限）
    return BaseResponse.success(jobLogRepository.findRecentFailures(limit));
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
  public BaseResponse<List<Map<String, Object>>> heatmap(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    LocalDate queryDate = date != null ? date : LocalDate.now();
    List<Map<String, Object>> heatmap = new ArrayList<>();
    for (int hour = 0; hour < 24; hour++) {
      LocalDateTime hourStart = queryDate.atTime(hour, 0);
      LocalDateTime hourEnd = queryDate.atTime(hour, 59, 59);
      // 通过 Repository 统计每小时的执行数量
      long count = jobLogRepository.countByTimeRange(hourStart, hourEnd);
      Map<String, Object> entry = new HashMap<>();
      entry.put("hour", hour);
      entry.put("count", count);
      heatmap.add(entry);
    }
    return BaseResponse.success(heatmap);
  }
}
