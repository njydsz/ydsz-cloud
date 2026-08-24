package com.njydsz.cronjob.server.core.dispatch;

import java.time.LocalDateTime;

import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行指标记录辅助类。
 *
 * <p>封装 Prometheus 指标记录、熔断计数更新等逻辑， 遵循云顶编码规范，将 {@link DefaultTaskDispatcher} 中的指标记录职责独立出来，
 * 降低主类复杂度，提升代码可维护性。
 *
 * <h3>职责范围</h3>
 *
 * <ul>
 *   <li>任务执行指标记录（派发次数、执行时长、失败次数）
 *   <li>线程池活跃度指标上报
 *   <li>熔断计数更新（成功归零，失败递增 + 达到阈值自动暂停）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JobExecutionMetricsHelper {

  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
  private final JobRepository jobRepository;

  /**
   * 构造指标记录辅助类。
   *
   * @param cronjobMetricsProvider Prometheus 指标收集器提供者
   * @param jobRepository 任务 Repository
   */
  public JobExecutionMetricsHelper(
      ObjectProvider<CronjobMetrics> cronjobMetricsProvider, JobRepository jobRepository) {
    this.cronjobMetricsProvider = cronjobMetricsProvider;
    this.jobRepository = jobRepository;
  }

  /**
   * 记录任务执行指标。
   *
   * <p>使用 try-catch 包裹，确保指标记录失败不影响主流程。
   *
   * @param job 任务定义
   * @param triggerType 触发类型
   * @param success 是否执行成功
   * @param log0 任务日志（含耗时信息）
   */
  public void recordJobMetrics(JobVO job, String triggerType, boolean success, JobLogVO log0) {
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics == null) {
      return;
    }
    try {
      String status = success ? "SUCCESS" : "FAILED";
      metrics.incJobDispatched(triggerType, status);
      metrics.recordJobDuration(
          job.getJobKey(), status, log0.getDurationMs() != null ? log0.getDurationMs() : 0L);
      if (!success) {
        metrics.incJobFailed(job.getJobKey());
      }
    } catch (Exception e) {
      log.debug("[Dispatcher] 指标记录失败(不影响主流程): key={} reason={}", job.getJobKey(), e.getMessage());
    }
  }

  /**
   * 定时上报线程池活跃度指标。
   *
   * <p>每 10 秒采集一次任务执行线程池的活跃率，供系统负载评分计算使用。
   *
   * @param activeCount 活跃线程数
   * @param maximumPoolSize 最大线程池大小
   */
  public void reportThreadPoolMetrics(int activeCount, int maximumPoolSize) {
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics == null) {
      return;
    }
    metrics.updatePoolActive(activeCount, maximumPoolSize);
  }

  /**
   * 熔断计数（成功归零，失败递增 + 达到阈值自动暂停）。
   *
   * @param job 任务定义
   * @param success 是否执行成功
   */
  public void updateCircuitBreaker(JobVO job, boolean success) {
    try {
      if (success) {
        jobRepository.resetConsecutiveFail(job.getId());
      } else {
        jobRepository.incrementConsecutiveFail(job.getId());
        Integer maxFails = job.getMaxConsecutiveFails();
        if (maxFails != null && maxFails > 0) {
          Integer current = jobRepository.findConsecutiveFailCount(job.getId());
          if (current != null && current >= maxFails) {
            jobRepository.markAutoPaused(job.getId());
            log.warn(
                "[Dispatcher] 任务熔断, 自动暂停: key={} consecutiveFails={}/{}",
                job.getJobKey(),
                current,
                maxFails);
          }
        }
      }
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] 熔断计数更新失败(不影响主流程): key={} reason={}",
          job.getJobKey(),
          e.getMessage());
    }
  }
}
