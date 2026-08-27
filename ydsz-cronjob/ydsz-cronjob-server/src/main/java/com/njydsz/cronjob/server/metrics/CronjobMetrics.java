package com.njydsz.cronjob.server.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;
import com.njydsz.cronjob.server.config.AdaptiveBatchConfig;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.executor.RunningTaskCounter;

/**
 * P6-2 任务调度 Prometheus 指标收集器
 *
 * <p>基于 Micrometer 暴露以下指标（通过 Spring Boot Actuator /actuator/prometheus）：
 *
 * <ul>
 * <li>Counter：任务派发/失败/超时/Misfire/告警派发计数
 *   <li>Timer：任务执行耗时分布
 *   <li>Gauge：运行中任务数、扫描器状态、上次扫描待触发数、系统负载评分
 * </ul>
 *
 * <p>所有指标前缀 {@code ydsz_cronjob_}，便于在 Grafana 看板中筛选。
 *
 * <p>Bean 名称 = {@code cronjobMetrics}，由 Spring 容器管理。 {@link RunningTaskCounter} 通过 {@link ObjectProvider}
 * 可选注入，从 Redis 读取运行中任务数，避免高频 Gauge 回调压垮 DB。
 *
 * <h3>指标清单</h3>
 *
 * <table>
 *   <tr><th>指标名</th><th>类型</th><th>Tags</th><th>说明</th></tr>
 *   <tr><td>ydsz_cronjob_job_dispatched_total</td><td>Counter</td><td>trigger_type, status</td><td>任务派发总数</td></tr>
 *   <tr><td>ydsz_cronjob_job_failed_total</td><td>Counter</td><td>job_key</td><td>任务失败总数</td></tr>
 *   <tr><td>ydsz_cronjob_job_timeout_total</td><td>Counter</td><td>job_key</td><td>任务超时总数</td></tr>
 *   <tr><td>ydsz_cronjob_misfire_total</td><td>Counter</td><td>policy</td><td>Misfire 触发总数</td></tr>
 *   <tr><td>ydsz_cronjob_alert_dispatched_total</td><td>Counter</td><td>alert_type, status</td><td>告警派发总数</td></tr>
 *   <tr><td>ydsz_cronjob_job_duration_ms</td><td>Timer</td><td>job_key, status</td><td>任务执行耗时</td></tr>
 *   <tr><td>ydsz_cronjob_job_running</td><td>Gauge</td><td>-</td><td>当前运行中任务数</td></tr>
 *   <tr><td>ydsz_cronjob_scanner_due_jobs</td><td>Gauge</td><td>-</td><td>上次扫描到的待触发任务数</td></tr>
 * <tr><td>ydsz_cronjob_scanner_scanning</td><td>Gauge</td><td>-</td><td>扫描器是否正在扫描（0/1）</td></tr>
 * <tr><td>ydsz_cronjob_system_load_score</td><td>Gauge</td><td>-</td><td>系统综合负载评分（0-1000）</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component("cronjobMetrics")
@ConditionalOnClass(MeterRegistry.class)
public class CronjobMetrics extends SentryMetricsAdapter {
  /** CPU 高负载权重 */
  private static final double CPU_WEIGHT_HIGH = 0.5;

  /** CPU 正常权重 */
  private static final double CPU_WEIGHT_LOW = 0.4;

  /** 内存高负载权重 */
  private static final double MEM_WEIGHT_HIGH = 0.4;

  /** 内存正常权重 */
  private static final double MEM_WEIGHT_LOW = 0.3;

  /** 线程池高负载权重 */
  private static final double POOL_WEIGHT_HIGH = 0.4;

  /** 线程池正常权重 */
  private static final double POOL_WEIGHT_LOW = 0.3;


  /** P1-2: 运行中任务数计数器（Redis 维护，替代 DB 查询） */
  private final ObjectProvider<RunningTaskCounter> runningTaskCounterProvider;

  // ============================== Gauge 状态字段（由 Scanner 更新，Gauge 回调读取）
  // ==============================
  /** 上次扫描到的待触发任务数 */
  private final AtomicLong lastScanDueJobs = new AtomicLong(0);

  /** 扫描器扫描中标志（0=空闲，1=扫描中） */
  private final AtomicLong scanningFlag = new AtomicLong(0);

  /** 自适应批量大小 */
  private final AtomicLong adaptiveBatchSize = new AtomicLong(0);

  /** 系统负载评分（0-1000，由 collectSystemLoadMetrics 定时更新） */
  private final AtomicLong systemLoadScore = new AtomicLong(0);

  /** 线程池活跃度（0-100，由 DefaultTaskDispatcher 更新） */
  private final AtomicInteger poolActivePct = new AtomicInteger(0);

  private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
  private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();

  // ============================== Gauge 数据源（可选注入，避免循环依赖） ==============================
  /** 调度引擎配置属性 */
  private final CronjobProperties cronjobProperties;

  /** Gauge 值引用：运行中任务数 */
  private final AtomicReference<Double> runningJobsRef = new AtomicReference<>(0.0);

  public CronjobMetrics(
      ObjectProvider<RunningTaskCounter> runningTaskCounterProvider,
      CronjobProperties cronjobProperties) {
    super("ydsz_cronjob_");
    this.runningTaskCounterProvider = runningTaskCounterProvider;
    this.cronjobProperties = cronjobProperties;
    registerGauges();
    log.info("[CronjobMetrics] 初始化完成，Prometheus 端点可访问 /actuator/prometheus");
  }

  /**
   * P0-6/11: 注册自身引用到 CronjobMetricsHolder，使旧静态方法委托到本 Bean。
   *
   * <p>兼容过渡期使用，新代码应直接注入 {@link CronjobMetrics}。
   */
  @PostConstruct
  public void registerAsHolderDelegate() {
    CronjobMetricsHolder.setCronjobMetrics(this);
    log.info("[CronjobMetrics] 已注册为 CronjobMetricsHolder 委托目标");
  }

  // ===========================================
  // Counter：任务派发
  // ===========================================

  /**
   * 任务派发计数（按触发类型与结果状态分类）。
   *
   * @param triggerType 触发类型：CRON / MANUAL / RETRY / DEPENDENT / MISFIRED
   * @param status 执行结果：SUCCESS / FAILED / TIMEOUT
   */
  public void incJobDispatched(String triggerType, String status) {
    counter("job_dispatched_total", "trigger_type", safe(triggerType), "status", safe(status))
        .increment();
  }

  /**
   * 任务成功计数（按 job_key 分类，便于定位高频成功任务）。
   *
   * <p>P0-FIX: Outbox MetricsOutboxSubscriber 消费 JOB_SUCCESS 事件时调用，原实现缺失。
   *
   * @param jobKey 任务 KEY
   */
  public void incJobSuccess(String jobKey) {
    counter("job_success_total", "job_key", safe(jobKey)).increment();
  }

  /**
   * 任务失败计数（按 job_key 分类，便于定位高频失败任务）。
   *
   * @param jobKey 任务 KEY
   */
  public void incJobFailed(String jobKey) {
    counter("job_failed_total", "job_key", safe(jobKey)).increment();
  }

  /**
   * 任务超时计数（按 job_key 分类）。
   *
   * @param jobKey 任务 KEY
   */
  public void incJobTimeout(String jobKey) {
    counter("job_timeout_total", "job_key", safe(jobKey)).increment();
  }

  /**
   * Misfire 计数（按策略分类）。
   *
   * @param policy Misfire 策略：SKIP / FIRE_NOW / COALESCE
   */
  public void incMisfire(String policy) {
    counter("misfire_total", "policy", safe(policy)).increment();
  }

  // ===========================================
  // Counter：告警
  // ===========================================

  /**
   * 告警派发计数（按告警类型与结果状态分类）。
   *
   * @param alertType 告警类型：FAIL / SLOW / TIMEOUT / DURATION_P95 / FAIL_RATE
   * @param status 派发结果：SUCCESS / PARTIAL / FAILED / SKIPPED
   */
  public void incAlertDispatched(String alertType, String status) {
    counter("alert_dispatched_total", "alert_type", safe(alertType), "status", safe(status))
        .increment();
  }

  // ===========================================
  // P1-10: 补充指标 — DAG / 分片 / 重试
  // ===========================================

  /**
   * P1-10: DAG 执行计数（按状态分类）。
   *
   * @param dagKey DAG KEY
   * @param status 执行结果：SUCCESS / FAILED / TIMEOUT / PARTIAL_SUCCESS
   */
  public void incDagExecution(String dagKey, String status) {
    counter("dag_execution_total", "dag_key", safe(dagKey), "status", safe(status)).increment();
  }

  /**
   * P1-10: DAG 执行耗时记录。
   *
   * @param dagKey DAG KEY
   * @param status 执行结果
   * @param millis 耗时（毫秒）
   */
  public void recordDagDuration(String dagKey, String status, long millis) {
    if (millis < 0) {
      return;
    }
    timer("dag_duration_ms", "dag_key", safe(dagKey), "status", safe(status))
        .record(Duration.ofMillis(millis));
  }

  /**
   * P1-10: 分片任务派发计数。
   *
   * @param jobKey 任务 KEY
   * @param shardIndex 分片索引
   * @param status 执行结果
   */
  public void incShardDispatched(String jobKey, int shardIndex, String status) {
    counter(
            "shard_dispatched_total",
            "job_key",
            safe(jobKey),
            "shard",
            String.valueOf(shardIndex),
            "status",
            safe(status))
        .increment();
  }

  /**
   * P1-10: 任务重试计数（按触发类型分类）。
   *
   * @param jobKey 任务 KEY
   * @param triggerType 触发类型：RETRY / FAILOVER / SELF_HEALING
   */
  public void incJobRetry(String jobKey, String triggerType) {
    counter("job_retry_total", "job_key", safe(jobKey), "trigger_type", safe(triggerType))
        .increment();
  }

  /**
   * P1-10: 任务队列等待耗时记录（从入队到派发的耗时）。
   *
   * @param jobKey 任务 KEY
   * @param millis 等待耗时（毫秒）
   */
  public void recordQueueWaitDuration(String jobKey, long millis) {
    if (millis < 0) {
      return;
    }
    timer("job_queue_wait_ms", "job_key", safe(jobKey)).record(Duration.ofMillis(millis));
  }

  // ===========================================
  // P0-6/11: 从 CronjobMetricsHolder 迁移的指标方法
  // ===========================================

  /**
   * P0-6/11: 递增任务执行计数（按 job_key 分类）。
   *
   * <p>原 {@code CronjobMetricsHolder.incrementExecution} 迁移至此，统一使用 {@code ydsz_cronjob_} 前缀。
   *
   * @param jobKey 任务 KEY
   */
  public void incJobExecution(String jobKey) {
    counter("job_execution_total", "job_key", safe(jobKey)).increment();
  }

  /**
   * P0-6/11: 记录任务执行耗时分布（按 job_key 分类）。
   *
   * <p>原 {@code CronjobMetricsHolder.recordExecutionDuration} 迁移至此。
   *
   * @param jobKey 任务 KEY
   * @param millis 执行耗时（毫秒）
   */
  public void recordExecutionDuration(String jobKey, long millis) {
    if (millis < 0) {
      return;
    }
    timer("job_execution_duration_ms", "job_key", safe(jobKey)).record(Duration.ofMillis(millis));
  }

  /**
   * P0-6/11: 记录调度触发延迟（next_fire_time 到实际派发的延迟）。
   *
   * <p>原 {@code CronjobMetricsHolder.recordDispatchDelay} 迁移至此。
   *
   * @param jobKey 任务 KEY
   * @param delayMillis 触发延迟（毫秒，>= 0）
   */
  public void recordDispatchDelay(String jobKey, long delayMillis) {
    if (delayMillis < 0) {
      return;
    }
    timer("job_dispatch_delay_ms", "job_key", safe(jobKey)).record(Duration.ofMillis(delayMillis));
  }

  /**
   * P0-6/11: 递增分片成功计数。
   *
   * <p>原 {@code CronjobMetricsHolder.incrementShardSuccess} 迁移至此。
   *
   * @param jobKey 任务 KEY
   * @param shardIndex 分片索引
   */
  public void incShardSuccess(String jobKey, int shardIndex) {
    counter(
            "shard_success_total",
            "job_key",
            safe(jobKey),
            "shard_index",
            String.valueOf(shardIndex))
        .increment();
  }

  /**
   * P0-6/11: 递增分片失败计数。
   *
   * <p>原 {@code CronjobMetricsHolder.incrementShardFailure} 迁移至此。
   *
   * @param jobKey 任务 KEY
   * @param shardIndex 分片索引
   */
  public void incShardFailure(String jobKey, int shardIndex) {
    counter(
            "shard_failure_total",
            "job_key",
            safe(jobKey),
            "shard_index",
            String.valueOf(shardIndex))
        .increment();
  }

  // ===========================================
  // Timer：耗时
  // ===========================================

  /**
   * 记录任务执行耗时。
   *
   * @param jobKey 任务 KEY
   * @param status 执行结果：SUCCESS / FAILED / TIMEOUT
   * @param millis 耗时（毫秒）
   */
  public void recordJobDuration(String jobKey, String status, long millis) {
    if (millis < 0) {
      return;
    }
    timer("job_duration_ms", "job_key", safe(jobKey), "status", safe(status))
        .record(Duration.ofMillis(millis));
  }

  // ===========================================
  // P0-2: Leader 选举指标
  // ===========================================

  /** 当前 Leader 任期号（epoch/fencing token），Gauge 回调读取 */
  private final AtomicLong leaderEpoch = new AtomicLong(0);

  /**
   * P0-2: 递增 Leader 选举次数（每次抢占/重新抢占时调用）。
   *
   * @param role Leader 角色
   * @param result 选举结果：SUCCESS / FAILED
   */
  public void incLeaderElection(String role, String result) {
    counter("leader_election_total", "role", safe(role), "result", safe(result)).increment();
  }

  /**
   * P0-2: 记录 Leader 选举耗时（从开始抢占到成功的耗时）。
   *
   * @param role Leader 角色
   * @param millis 选举耗时（毫秒）
   */
  public void recordLeaderElectionDuration(String role, long millis) {
    if (millis < 0) {
      return;
    }
    timer("leader_election_duration_ms", "role", safe(role)).record(Duration.ofMillis(millis));
  }

  /**
   * P0-2: 更新当前 Leader 任期号（epoch/fencing token）。
   *
   * @param role Leader 角色
   * @param epoch 当前任期号
   */
  public void setLeaderEpoch(String role, long epoch) {
    leaderEpoch.set(epoch);
  }

  // ===========================================
  // Gauge：状态更新（由 Scanner/Dispatcher 调用）
  // ===========================================

  /**
   * 更新本次扫描到的待触发任务数（Gauge 回调读取）。
   *
   * @param count 待触发任务数
   */
  public void setLastScanDueJobs(int count) {
    lastScanDueJobs.set(count);
  }

  /**
   * 更新扫描中标志。
   *
   * @param scanning true=扫描中，false=空闲
   */
  public void setScanning(boolean scanning) {
    scanningFlag.set(scanning ? 1L : 0L);
  }

  /**
   * P1-1: 更新自适应批量大小。
   *
   * @param size 当前建议的 batchSize
   */
  public void setAdaptiveBatchSize(int size) {
    adaptiveBatchSize.set(size);
  }

  /**
   * 更新线程池活跃度（由 DefaultTaskDispatcher 定期调用）。
   *
   * @param activeThreads 活跃线程数
   * @param maxThreads 最大线程数
   */
  public void updatePoolActive(int activeThreads, int maxThreads) {
    if (maxThreads <= 0) {
      return;
    }
    int pct = (int) Math.min(100.0, (double) activeThreads / maxThreads * 100);
    poolActivePct.set(pct);
  }

  // ===========================================
  // 系统负载指标采集（原 AdaptiveBatchScheduler 逻辑合并）
  // ===========================================

  /**
   * 定时采集系统负载指标并发布到 Prometheus。
   *
   * <p>采集 CPU 使用率、堆内存使用率、线程池活跃度，计算综合负载评分。
   * 仅采集与上报，不做调度控制决策。
   */
  @Scheduled(fixedDelayString = "#{${ydsz.cronjob.adaptive-batch.eval-interval-seconds:10} * 1000}")
  public void collectSystemLoadMetrics() {
    try {
      double cpuUsage = getCpuUsage();
      double memUsage = getMemUsage();
      double poolActive = poolActivePct.get();
      double score = calculateLoadScore(cpuUsage, memUsage, poolActive);
      systemLoadScore.set((long) (score * 1000));
      cachedSystemLoadScore = systemLoadScore.get();
      log.debug(
          "[CronjobMetrics] 系统负载采集: cpu={}%, mem={}%, pool={}%",
          String.format("%.1f", cpuUsage),
          String.format("%.1f", memUsage),
          String.format("%.1f", poolActive));
    } catch (Exception e) {
      log.warn("[CronjobMetrics] 系统负载采集异常: {}", e.getMessage());
    }
  }

  /**
   * 获取 CPU 使用率（百分比，0-100）。
   *
   * <p>使用 {@link com.sun.management.OperatingSystemMXBean#getCpuLoad()}，返回 -1 时回退为 0。
   */
  private double getCpuUsage() {
    try {
      if (osMXBean instanceof com.sun.management.OperatingSystemMXBean sunOs) { // FQN-OK: name conflict with java.lang.management.OperatingSystemMXBean
        double load = sunOs.getCpuLoad();
        return load >= 0 ? load * 100 : 0;
      }
    } catch (Exception ignored) {
      // 降级处理
    }
    return 0;
  }

  /** 获取堆内存使用率（百分比，0-100）。 */
  private double getMemUsage() {
    try {
      long used = memoryMXBean.getHeapMemoryUsage().getUsed();
      long max = memoryMXBean.getHeapMemoryUsage().getMax();
      if (max <= 0) {
        return 0;
      }
      return (double) used / max * 100;
    } catch (Exception ignored) {
      return 0;
    }
  }

  /**
   * 计算综合负载评分（0-1）。
   *
   * <p>当任一指标超过对应阈值时，该项权重放大；均未超过时，按基线权重计算。
   */
  private double calculateLoadScore(double cpuUsage, double memUsage, double poolActive) {
    AdaptiveBatchConfig config = cronjobProperties.getAdaptiveBatch();
    double cpuScore = Math.min(1.0, cpuUsage / 100.0);
    double memScore = Math.min(1.0, memUsage / 100.0);
    double poolScore = Math.min(1.0, poolActive / 100.0);
    double cpuWeight = cpuUsage > config.getCpuThreshold() ? CPU_WEIGHT_HIGH : CPU_WEIGHT_LOW;
    double memWeight = memUsage > config.getMemThreshold() ? MEM_WEIGHT_HIGH : MEM_WEIGHT_LOW;
    double poolWeight = poolActive > config.getPoolActiveThreshold() ? POOL_WEIGHT_HIGH : POOL_WEIGHT_LOW;
    double totalWeight = cpuWeight + memWeight + poolWeight;
    return (cpuScore * cpuWeight + memScore * memWeight + poolScore * poolWeight) / totalWeight;
  }

  // ===========================================
  // 静态访问器（供诊断端点等非 Spring 场景读取）
  // ===========================================

  /** 当前系统负载评分（0-1000），由 {@link #collectSystemLoadMetrics()} 定时更新 */
  private static volatile long cachedSystemLoadScore = 0L;

  /**
   * 获取当前系统负载评分（供诊断端点静态调用）。
   *
   * <p>返回值范围 0-1000（除以 1000 得到 0-1 浮点值），由 {@link #collectSystemLoadMetrics()} 定时更新。
   *
   * @return 系统负载评分；未初始化时返回 0
   */
  public static long getSystemLoadScore() {
    return cachedSystemLoadScore;
  }

  // ===========================================
  // Gauge 注册
  // ===========================================

  private void registerGauges() {
    // 运行中任务数（通过 gaugeRef 注册可变引用，由 refreshRunningJobs 定期刷新）
    gaugeRef("job_running", runningJobsRef, AtomicReference::get);

    // 上次扫描到的待触发任务数
    gaugeRef("scanner_due_jobs", lastScanDueJobs, AtomicLong::doubleValue);

    // 扫描器扫描中标志
    gaugeRef("scanner_scanning", scanningFlag, AtomicLong::doubleValue);

    // P1-1: 自适应批量大小
    gaugeRef("adaptive_batch_size", adaptiveBatchSize, AtomicLong::doubleValue);

    // P1-1: 系统负载评分（0-1000，除以1000得到 0-1）
    gaugeRef("system_load_score", systemLoadScore, AtomicLong::doubleValue);

    // P0-2: Leader 任期号（epoch/fencing token）
    gaugeRef("leader_epoch", leaderEpoch, AtomicLong::doubleValue);
  }

  /**
   * 刷新运行中任务数 Gauge（由定时任务调用）。
   *
   * <p>从 Redis 读取最新运行中任务数并更新到 AtomicReference，消除 DB 查询。
   */
  public void refreshRunningJobs() {
    try {
      RunningTaskCounter counter = runningTaskCounterProvider.getIfAvailable();
      long count = counter != null ? counter.getCount() : 0L;
      runningJobsRef.set((double) count);
    } catch (Exception e) {
      log.debug("[CronjobMetrics] refresh job_running 读取失败: {}", e.getMessage());
      runningJobsRef.set(0.0);
    }
  }
}
