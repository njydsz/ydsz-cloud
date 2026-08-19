package com.njydsz.cronjob.server.core.canary;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;

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
 * <h3>灰度状态机</h3>
 *
 * <pre>
 * IDLE → CANARYING → PROMOTED (全量发布)
 *           ↓
 *        ROLLED_BACK (回滚)
 * </pre>
 *
 * <h3>对标</h3>
 *
 * <p>对标 Kubernetes Canary Deployment、Istio Traffic Splitting、Nginx 灰度发布。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CanaryReleaseService {

  private final JobMapper jobMapper;

  /** 灰度执行统计：jobKey -> 统计信息 */
  private final ConcurrentHashMap<String, CanaryStats> canaryStatsMap = new ConcurrentHashMap<>();

  /**
   * 启动灰度发布。
   *
   * <p>设置任务的 canaryRatio 和 canaryHandler，初始化灰度统计。
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
    Job job = jobMapper.selectByJobKey(jobKey);
    if (job == null) {
      log.warn("[Canary] 任务不存在: jobKey={}", jobKey);
      return false;
    }
    // 设置灰度配置
    job.setCanaryHandler(canaryHandler);
    job.setCanaryRatio(initialRatio);
    jobMapper.updateById(job);

    // 初始化统计
    canaryStatsMap.put(jobKey, new CanaryStats(jobKey, canaryHandler, initialRatio));
    log.info("[Canary] 启动灰度发布: jobKey={} canaryHandler={} ratio={}%",
        jobKey, canaryHandler, initialRatio);
    return true;
  }

  /**
   * 调整灰度比例。
   *
   * <p>根据灰度观测结果逐步提升（或降低）灰度流量比例。
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
    Job job = jobMapper.selectByJobKey(jobKey);
    if (job == null || job.getCanaryHandler() == null) {
      log.warn("[Canary] 任务不在灰度状态: jobKey={}", jobKey);
      return false;
    }
    int oldRatio = job.getCanaryRatio() != null ? job.getCanaryRatio() : 0;
    job.setCanaryRatio(newRatio);
    jobMapper.updateById(job);

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
   * <p>由 {@code DefaultTaskDispatcher} 在任务执行完成后调用，记录成功/失败/耗时。
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
   * <p>将 canaryHandler 复制到 handler，清除灰度配置。
   *
   * @param jobKey 任务 KEY
   * @return true 表示发布成功
   */
  public boolean promote(String jobKey) {
    Job job = jobMapper.selectByJobKey(jobKey);
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
    jobMapper.updateById(job);

    // 清除统计
    canaryStatsMap.remove(jobKey);
    log.info("[Canary] 全量发布: jobKey={} handler: {} -> {}", jobKey, oldHandler, canaryHandler);
    return true;
  }

  /**
   * 回滚灰度发布。
   *
   * <p>清除灰度配置，恢复到原 handler。
   *
   * @param jobKey 任务 KEY
   * @return true 表示回滚成功
   */
  public boolean rollback(String jobKey) {
    Job job = jobMapper.selectByJobKey(jobKey);
    if (job == null || job.getCanaryHandler() == null) {
      log.warn("[Canary] 任务不在灰度状态: jobKey={}", jobKey);
      return false;
    }
    String canaryHandler = job.getCanaryHandler();
    job.setCanaryHandler(null);
    job.setCanaryRatio(0);
    jobMapper.updateById(job);

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
   * <p>记录灰度版本的执行成功率、平均耗时、P95 耗时等指标。
   */
  public static class CanaryStats {

    private final String jobKey;
    private final String canaryHandler;
    private int initialRatio;
    private long totalExecutions;
    private long successCount;
    private long failureCount;
    private double avgDurationMs;
    private long p95DurationMs;
    private final LocalDateTime startTime;

    CanaryStats(String jobKey, String canaryHandler, int initialRatio) {
      this(jobKey, canaryHandler, initialRatio, 0, 0, 0, 0.0, 0, LocalDateTime.now());
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
      this.totalExecutions = totalExecutions;
      this.successCount = successCount;
      this.failureCount = failureCount;
      this.avgDurationMs = avgDurationMs;
      this.p95DurationMs = p95DurationMs;
      this.startTime = startTime;
    }

    /** 更新灰度比例。 */
    void updateRatio(int newRatio) {
      initialRatio = newRatio;
    }

    /** 记录一次执行结果。 */
    synchronized void recordExecution(boolean success, long durationMs) {
      totalExecutions++;
      if (success) {
        successCount++;
      } else {
        failureCount++;
      }
      avgDurationMs = (avgDurationMs * (totalExecutions - 1) + durationMs) / totalExecutions;
    }

    /** 计算成功率。 */
    public double getSuccessRate() {
      return totalExecutions == 0 ? 0.0 : (successCount * 100.0) / totalExecutions;
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
      return totalExecutions;
    }

    public long getSuccessCount() {
      return successCount;
    }

    public long getFailureCount() {
      return failureCount;
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
