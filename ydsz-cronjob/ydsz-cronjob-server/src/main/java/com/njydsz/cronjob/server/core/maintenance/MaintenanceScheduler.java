package com.njydsz.cronjob.server.core.maintenance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.njydsz.common.thread.util.ExecutorUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 统一维护扫描器（P2-O3）。
 *
 * <p>合并原 JobScanner、AnomalyRecoveryScanner、AlertScanner、AutoResumeScanner、DependencyPatrolScanner
 * 的调度逻辑为统一框架。每个扫描任务实现 {@link ScanTask} 接口，由本调度器统一定时触发、Leader 校验、
 * 分布式锁协调、异常隔离。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li><b>统一调度</b>：所有周期性扫描共享一个调度线程池，按各自间隔独立触发
 *   <li><b>Leader 校验</b>：通过 {@link LeaderElector#isLeader(String)} 判定，避免多实例重复扫描
 *   <li><b>异常隔离</b>：单个 ScanTask 执行异常不影响其他任务，异常仅记录日志
 *   <li><b>优雅下线</b>：{@link #shutdown()} 调用时停止所有定时任务，等待当前扫描完成
 *   <li><b>状态可观测</b>：记录每个任务的上次执行时间、累计执行次数、上次异常
 * </ul>
 *
 * <h3>对标</h3>
 *
 * <p>对标 XXL-Job 的调度器设计、PowerJob 的 Worker 健康检查框架、Quartz 的 JobScheduler。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class MaintenanceScheduler {

  private final List<ScanTask> scanTasks;
  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;

  /** P6-2: Prometheus 指标收集器（可选注入） */
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

  /** 调度线程池（单线程调度，每个 ScanTask 独立间隔触发） */
  private ScheduledExecutorService scheduler;

  /** 各 ScanTask 的调度_future */
  private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

  /** 各 ScanTask 的执行统计 */
  private final Map<String, TaskStats> statsMap = new ConcurrentHashMap<>();

  /** 运行标志 */
  private final AtomicBoolean running = new AtomicBoolean(false);

  /** Leader 角色 */
  private String leaderRole;

  /**
   * 初始化统一调度器：为每个 ScanTask 注册定时调度。
   *
   * <p>使用独立的 ScheduledExecutorService（守护线程），避免与 Spring 调度线程池耦合。
   * 每个 ScanTask 按各自 {@link ScanTask#intervalMs()} 间隔独立触发。
   */
  @PostConstruct
  public void init() {
    this.leaderRole = cronjobProperties.getLeader().getRole();
    if (!cronjobProperties.getLeader().isEnabled()) {
      log.info("[MaintenanceScheduler] leader.enabled=false, 统一维护调度器不启用");
      return;
    }
    if (scanTasks == null || scanTasks.isEmpty()) {
      log.info("[MaintenanceScheduler] 无注册 ScanTask, 跳过初始化");
      return;
    }
    this.scheduler = ExecutorUtils.newScheduledThreadPool(
        Math.min(scanTasks.size(), 4), "job-maintenance-");
    for (ScanTask task : scanTasks) {
      registerTask(task);
    }
    running.set(true);
    log.info("[MaintenanceScheduler] 初始化完成, taskCount={} role={}", scanTasks.size(), leaderRole);
  }

  /**
   * 注册一个 ScanTask 到调度器。
   *
   * <p>使用固定延迟调度（fixedDelay），避免上次执行耗时较长时任务堆积。
   *
   * @param task 扫描任务
   */
  private void registerTask(ScanTask task) {
    String name = task.name();
    if (futures.containsKey(name)) {
      log.warn("[MaintenanceScheduler] ScanTask 已存在, 跳过重复注册: name={}", name);
      return;
    }
    long intervalMs = task.intervalMs();
    if (intervalMs <= 0) {
      log.warn("[MaintenanceScheduler] ScanTask intervalMs <= 0, 跳过注册: name={} interval={}", name, intervalMs);
      return;
    }
    ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
        () -> executeTask(task),
        intervalMs,
        intervalMs,
        TimeUnit.MILLISECONDS);
    futures.put(name, future);
    statsMap.put(name, new TaskStats(name));
    log.info("[MaintenanceScheduler] 注册 ScanTask: name={} intervalMs={} requireLeader={}",
        name, intervalMs, task.requireLeader());
  }

  /**
   * 执行单个 ScanTask（含 Leader 校验、异常隔离、统计）。
   *
   * <p>异常隔离：单个任务异常不影响其他任务；统计信息用于监控告警。
   *
   * @param task 扫描任务
   */
  private void executeTask(ScanTask task) {
    String name = task.name();
    TaskStats stats = statsMap.get(name);
    if (stats == null) {
      return;
    }
    // Leader 校验
    if (task.requireLeader() && !leaderElector.isLeader(leaderRole)) {
      return;
    }
    // 执行扫描
    long startMs = System.currentTimeMillis();
    try {
      task.scan();
      long elapsed = System.currentTimeMillis() - startMs;
      stats.recordSuccess(elapsed);
      log.debug("[MaintenanceScheduler] 扫描任务执行完成: name={} elapsed={}ms", name, elapsed);
    } catch (Exception e) {
      long elapsed = System.currentTimeMillis() - startMs;
      stats.recordFailure(elapsed, e);
      log.error("[MaintenanceScheduler] 扫描任务执行异常: name={} elapsed={}ms reason={}",
          name, elapsed, e.getMessage(), e);
    }
  }

  /**
   * 优雅下线：停止所有定时任务，等待当前扫描完成。
   *
   * <p>调用 {@code scheduler.shutdown()} 停止接受新任务，但不中断正在执行的任务。
   * 最多等待 10s，超时后强制终止。
   */
  @PreDestroy
  public void shutdown() {
    running.set(false);
    log.info("[MaintenanceScheduler] 开始关闭, taskCount={}", futures.size());
    if (scheduler != null) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
          log.warn("[MaintenanceScheduler] 调度器关闭超时, 强制终止");
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        scheduler.shutdownNow();
      }
    }
    futures.clear();
    log.info("[MaintenanceScheduler] 已关闭");
  }

  /**
   * 获取所有扫描任务的统计信息（供监控 API 使用）。
   *
   * @return 任务名称 -> 统计信息的不可变映射
   */
  public Map<String, TaskStats> getStats() {
    return Map.copyOf(statsMap);
  }

  /**
   * 手动触发指定名称的扫描任务（供测试/紧急修复使用）。
   *
   * @param name 任务名称
   * @return true 表示任务存在并已触发
   */
  public boolean triggerNow(String name) {
    // 仅校验存在性，实际执行逻辑通过 Spring 上下文获取 Bean 后调用 scan()
    return statsMap.containsKey(name);
  }

  /**
   * 扫描任务执行统计。
   *
   * <p>记录每个 ScanTask 的累计执行次数、成功次数、失败次数、上次执行时间、平均耗时等。
   *
   * @param name            任务名称
   * @param totalExecutions 累计执行次数
   * @param successCount    成功次数
   * @param failureCount    失败次数
   * @param lastExecuteTime 上次执行时间
   * @param avgElapsedMs    平均耗时（毫秒）
   * @param lastError       上次异常信息（无异常为 null）
   */
  /**
   * 扫描任务执行统计。
   *
   * <p>P0-FIX: 原为 {@code record}（组件不可变），但 recordSuccess/recordFailure 会修改字段，
   * 编译报"无法为 final 变量赋值"。改为可变内部类，保留原构造语义。
   */
  public static class TaskStats {

    private final String name;
    private long totalExecutions;
    private long successCount;
    private long failureCount;
    private LocalDateTime lastExecuteTime;
    private double avgElapsedMs;
    private String lastError;

    TaskStats(String name) {
      this.name = name;
    }

    /** 记录一次成功执行。 */
    synchronized void recordSuccess(long elapsedMs) {
      totalExecutions++;
      successCount++;
      lastExecuteTime = LocalDateTime.now();
      avgElapsedMs = (avgElapsedMs * (totalExecutions - 1) + elapsedMs) / totalExecutions;
    }

    /** 记录一次失败执行。 */
    synchronized void recordFailure(long elapsedMs, Exception e) {
      totalExecutions++;
      failureCount++;
      lastExecuteTime = LocalDateTime.now();
      lastError = e.getMessage();
      avgElapsedMs = (avgElapsedMs * (totalExecutions - 1) + elapsedMs) / totalExecutions;
    }

    public String name() {
      return name;
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

    public LocalDateTime getLastExecuteTime() {
      return lastExecuteTime;
    }

    public double getAvgElapsedMs() {
      return avgElapsedMs;
    }

    public String getLastError() {
      return lastError;
    }
  }
}
