package com.njydsz.cronjob.server.core.canary;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 灰度发布管理服务（P2-4：任务配置版本管理 + 灰度发布）。
 *
 * <p>在 P1-6 灰度路由的基础上，提供灰度发布全生命周期管理：
 *
 * <ul>
 *   <li><b>启动灰度</b>：设置 canaryRatio 和 canaryHandler，指定灰度流量比例
 *   <li><b>灰度观测</b>：实时追踪灰度版本的执行成功率、耗时、异常率
 *   <li><b>灰度推进</b>：根据观测结果逐步提升灰度比例（10% → 30% → 50% → 100%）
 *   <li><b>一键回滚</b>：灰度异常时立即回滚到原版本
 * </ul>
 *
 * <p>P2-修正：使用 JobRepository 替换 JobMapper，使用 JobVO 替换 Job 实体，符合 DDD 分层规范。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CanaryReleaseService {

  private final JobRepository jobRepository;

  /** 灰度执行统计：jobKey -> 统计信息 */
  private final ConcurrentHashMap<String, CanaryStats> canaryStatsMap = new ConcurrentHashMap<>();

  /**
   * 启动灰度发布。
   *
   * @param jobKey         任务 KEY
   * @param canaryHandler  灰度处理器 Bean 名称
   * @param initialRatio   初始灰度比例（推荐 10-20）
   * @return true 表示启动成功
   */
  public boolean startCanary(String jobKey, String canaryHandler, int initialRatio) {
    if (initialRatio < 1 || initialRatio > 100) {
      log.warn("[Canary] 灰度比例无效: jobKey={} ratio={}", jobKey, initialRatio);
      return false;
    }
    JobVO job = jobRepository.findByJobKey(jobKey).orElse(null);
    if (job == null) {
      log.warn("[Canary] 任务不存在: jobKey={}", jobKey);
      return false;
    }
    // 设置灰度配置
    job.setCanaryHandler(canaryHandler);
    job.setCanaryRatio(initialRatio);
    jobRepository.updateById(job);

    // 初始化统计
    canaryStatsMap.put(jobKey, new CanaryStats(jobKey, canaryHandler, initialRatio));
    log.info("[Canary] 启动灰度发布: jobKey={} canaryHandler={} ratio={}%",
        jobKey, canaryHandler, initialRatio);
    return true;
  }

  /**
   * 调整灰度比例。
   *
   * @param jobKey    任务 KEY
   * @param newRatio  新的灰度比例
   * @return true 表示调整成功
   */
  public boolean adjustRatio(String jobKey, int newRatio) {
    if (newRatio < 0 || newRatio > 100) {
      log.warn("[Canary] 灰度比例无效: jobKey={} ratio={}", jobKey, newRatio);
      return false;
    }
    JobVO job = jobRepository.findByJobKey(jobKey).orElse(null);
    if (job == null || job.getCanaryHandler() == null) {
      log.warn("[Canary] 任务不在灰度状态: jobKey={}", jobKey);
      return false;
    }
    int oldRatio = job.getCanaryRatio() != null ? job.getCanaryRatio() : 0;
    job.setCanaryRatio(newRatio);
    jobRepository.updateById(job);

    CanaryStats stats = canaryStatsMap.get(jobKey);
    if (stats != null) {
      stats.updateRatio(newRatio);
    }
    log.info("[Canary] 调整灰度比例: jobKey={} oldRatio={}% newRatio={}%", jobKey, oldRatio, newRatio);
    return true;
  }

  /**
   * 记录灰度执行结果。
   *
   * @param jobKey    任务 KEY
   * @param success   是否成功
   * @param durationMs 执行耗时（毫秒）
   */
  public void recordExecution(String jobKey, boolean success, long durationMs) {
    CanaryStats stats = canaryStatsMap.get(jobKey);
    if (stats != null) {
      stats.recordExecution(success, durationMs);
    }
  }

  /**
   * 全量发布（P1-6 灰度完成）。
   *
   * @param jobKey 任务 KEY
   * @return true 表示发布成功
   */
  public boolean promote(String jobKey) {
    JobVO job = jobRepository.findByJobKey(jobKey).orElse(null);
    if (job == null || job.getCanaryHandler() == null) {
      log.warn("[Canary] 任务不在灰度状态: jobKey={}", jobKey);
      return false;
    }
    String oldHandler = job.getHandler();
    String canaryHandler = job.getCanaryHandler();

    // 将灰度 handler 提升为正式 handler
    job.setHandler(canaryHandler);
    job.setCanaryHandler(null);
    job.setCanaryRatio(0);
    jobRepository.updateById(job);

    // 清除统计
    canaryStatsMap.remove(jobKey);
    log.info("[Canary] 全量发布: jobKey={} handler: {} -> {}", jobKey, oldHandler, canaryHandler);
    return true;
  }

  /**
   * 回滚灰度发布。
   *
   * @param jobKey 任务 KEY
   * @return true 表示回滚成功
   */
  public boolean rollback(String jobKey) {
    JobVO job = jobRepository.findByJobKey(jobKey).orElse(null);
    if (job == null || job.getCanaryHandler() == null) {
      log.warn("[Canary] 任务不在灰度状态: jobKey={}", jobKey);
      return false;
    }
    String canaryHandler = job.getCanaryHandler();
    job.setCanaryHandler(null);
    job.setCanaryRatio(0);
    jobRepository.updateById(job);

    // 清除统计
    canaryStatsMap.remove(jobKey);
    log.info("[Canary] 灰度回滚: jobKey={} 回滚到 handler={}, 清除 canaryHandler={}",
        jobKey, job.getHandler(), canaryHandler);
    return true;
  }

  /**
   * 获取灰度统计信息。
   *
   * @param jobKey 任务 KEY
   * @return 灰度统计信息（不在灰度状态返回 null）
   */
  public CanaryStats getStats(String jobKey) {
    return canaryStatsMap.get(jobKey);
  }

  /**
   * 灰度任务运行统计。
   *
   * <p>P0-12: 使用 {@link LongAdder} 替代 {@code synchronized}，消除高并发下统计更新的锁竞争。
   * 平均耗时使用 CAS 增量更新，P95 由外部定时聚合计算（此处保留字段供查询）。
   */
  public static class CanaryStats {

    private final String jobKey;
    private final String canaryHandler;
    private int initialRatio;
    private final LongAdder totalExecutions = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder durationSum = new LongAdder();
    private volatile double avgDurationMs;
    private volatile long p95DurationMs;
    private final LocalDateTime startTime;

    CanaryStats(String jobKey, String canaryHandler, int initialRatio) {
      this.jobKey = jobKey;
      this.canaryHandler = canaryHandler;
      this.initialRatio = initialRatio;
      this.startTime = LocalDateTime.now();
    }

    CanaryStats(
        String jobKey,
        String canaryHandler,
        int initialRatio,
        long totalExecutions,
        long successCount,
        long failureCount,
        double avgDurationMs,
        long p95DurationMs,
        LocalDateTime startTime) {
      this.jobKey = jobKey;
      this.canaryHandler = canaryHandler;
      this.initialRatio = initialRatio;
      this.totalExecutions.add(totalExecutions);
      this.successCount.add(successCount);
      this.failureCount.add(failureCount);
      this.avgDurationMs = avgDurationMs;
      this.p95DurationMs = p95DurationMs;
      this.startTime = startTime;
    }

    void updateRatio(int newRatio) {
      initialRatio = newRatio;
    }

    /**
     * P0-12: 记录执行结果（无锁，使用 LongAdder）。
     *
     * @param success 是否成功
     * @param durationMs 执行耗时
     */
    void recordExecution(boolean success, long durationMs) {
      totalExecutions.increment();
      if (success) {
        successCount.increment();
      } else {
        failureCount.increment();
      }
      durationSum.add(durationMs);
      avgDurationMs = durationSum.sum() / (double) totalExecutions.sum();
    }

    public double getSuccessRate() {
      long total = totalExecutions.sum();
      return total == 0 ? 0.0 : (successCount.sum() * 100.0) / total;
    }

    public String jobKey() {
      return jobKey;
    }

    public String canaryHandler() {
      return canaryHandler;
    }

    public int initialRatio() {
      return initialRatio;
    }

    public long getTotalExecutions() {
      return totalExecutions.sum();
    }

    public long getSuccessCount() {
      return successCount.sum();
    }

    public long getFailureCount() {
      return failureCount.sum();
    }

    public double getAvgDurationMs() {
      return avgDurationMs;
    }

    public long getP95DurationMs() {
      return p95DurationMs;
    }

    public LocalDateTime startTime() {
      return startTime;
    }
  }
}
