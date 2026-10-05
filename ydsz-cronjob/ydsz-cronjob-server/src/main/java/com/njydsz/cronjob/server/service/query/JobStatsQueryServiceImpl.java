package com.njydsz.cronjob.server.service.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.constants.CronjobConstants;
import com.njydsz.cronjob.domain.repository.JobDailyStatsRepository;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.service.JobStatsQueryService;
import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 任务执行统计查询 Service 实现（server 层）。
 *
 * <p>封装每日统计、汇总、热力图等查询，遵循 DDD 分层。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobStatsQueryServiceImpl implements JobStatsQueryService {

  private static final int COLLECTION_CAPACITY = 16;
  private static final int HOURS_PER_DAY = 24;
  private static final int MINUTE_END = 59;
  private static final int SECOND_END = 59;

  private final JobDailyStatsRepository jobDailyStatsRepository;
  private final JobLogRepository jobLogRepository;
  private final JobRepository jobRepository;
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

  @Override
  public List<JobDailyStatsVO> getDailyStats(String jobId, LocalDate startDate, LocalDate endDate) {
    return jobDailyStatsRepository.findByJobIdAndDateRange(jobId, startDate, endDate);
  }

  @Override
  public Map<String, Object> getSummary(String jobId, LocalDate startDate, LocalDate endDate) {
    List<JobDailyStatsVO> list =
        jobDailyStatsRepository.findByJobIdAndDateRange(jobId, startDate, endDate);
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
    Map<String, Object> summary = new HashMap<>(COLLECTION_CAPACITY);
    summary.put("jobId", jobId);
    summary.put("startDate", startDate);
    summary.put("endDate", endDate);
    summary.put("fireCount", fireCount);
    summary.put("successCount", successCount);
    summary.put("failCount", failCount);
    summary.put("timeoutCount", timeoutCount);
    summary.put("avgDurationMs", durationSamples > 0 ? totalDuration / durationSamples : 0L);
    return summary;
  }

  @Override
  public Map<String, Object> getDashboard() {
    Map<String, Object> dashboard = new HashMap<>(COLLECTION_CAPACITY);
    Map<String, Object> taskStats = new HashMap<>(COLLECTION_CAPACITY);
    taskStats.put("total", jobRepository.countAll());
    taskStats.put("normal", jobRepository.countByStatus(CronjobConstants.JOB_STATUS_NORMAL));
    taskStats.put("paused", jobRepository.countByStatus(CronjobConstants.JOB_STATUS_PAUSED));
    taskStats.put("error", jobRepository.countByStatus(CronjobConstants.JOB_STATUS_ERROR));
    taskStats.put("autoPaused", jobRepository.countByStatus(CronjobConstants.JOB_STATUS_AUTO_PAUSED));
    dashboard.put("taskStats", taskStats);

    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    Map<String, Object> todayExec = new HashMap<>(COLLECTION_CAPACITY);
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

    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics != null) {
      Map<String, Object> systemMetrics = new HashMap<>(COLLECTION_CAPACITY);
      systemMetrics.put("running", todayRunning);
      dashboard.put("systemMetrics", systemMetrics);
    }

    return dashboard;
  }

  @Override
  public List<JobLogVO> getRecentFailures(int limit) {
    return jobLogRepository.findRecentFailures(limit);
  }

  @Override
  public List<Map<String, Object>> getHeatmap(LocalDate date) {
    LocalDate queryDate = date != null ? date : LocalDate.now();
    List<Map<String, Object>> heatmap = new ArrayList<>(COLLECTION_CAPACITY);
    for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
      LocalDateTime hourStart = queryDate.atTime(hour, 0);
      LocalDateTime hourEnd = queryDate.atTime(hour, MINUTE_END, SECOND_END);
      long count = jobLogRepository.countByTimeRange(hourStart, hourEnd);
      Map<String, Object> entry = new HashMap<>(COLLECTION_CAPACITY);
      entry.put("hour", hour);
      entry.put("count", count);
      heatmap.add(entry);
    }
    return heatmap;
  }
}
