package com.njydsz.cronjob.web.controller.dashboard;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.repository.JobDagInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.executor.RunningTaskCounter;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 健康仪表盘 Controller（P1-2：前端控制台交互增强）。
 *
 * <p>聚合调度引擎的多维度运行状态，为前端控制台提供一站式健康检查能力：
 *
 * <ul>
 *   <li>系统资源：CPU / 内存 / 线程池使用率
 *   <li>任务概览：总数 / 各状态分布 / 今日执行统计
 *   <li>DAG 工作流：运行中实例数 / 成功率
 *   <li>调度器状态：Leader 节点 / 扫描器状态 / 队列积压
 *   <li>最近异常：失败任务 Top N / 超时任务
 * </ul>
 *
 * <p>适用于运维人员快速定位系统异常、评估集群健康度。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Tag(name = "健康仪表盘", description = "聚合系统资源、任务、DAG、调度器多维度运行状态")
@RestController
@RequestMapping("/api/v1/cronjob/dashboard/health")
@RequiredArgsConstructor
public class HealthDashboardController {

  /** Map 初始容量：16 */
  private static final int MAP_CAPACITY_16 = 16;

  /** Map 初始容量：8 */
  private static final int MAP_CAPACITY_8 = 8;

  /** Map 初始容量：4 */
  private static final int MAP_CAPACITY_4 = 4;

  /** 最近失败任务条数 */
  private static final int RECENT_FAILURES_LIMIT = 10;

  /** 线程池使用率警告阈值 */
  private static final int POOL_USAGE_WARN_THRESHOLD = 80;

  /** 内存使用率警告阈值 */
  private static final int MEMORY_USAGE_WARN_THRESHOLD = 85;

  /** Leader 选举角色 */
  private static final String LEADER_ROLE = "ydsz-job-scheduler";

  /** 系统资源危险阈值（CPU/内存使用率百分比） */
  private static final int CRITICAL_USAGE_THRESHOLD = 95;

  /** WARNING 级别健康评分扣分 */
  private static final int WARNING_SCORE_DEDUCTION = 20;

  /** CRITICAL 级别健康评分扣分 */
  private static final int CRITICAL_SCORE_DEDUCTION = 50;

  /** 任务异常扣分上限 */
  private static final int ERROR_SCORE_MAX_DEDUCTION = 20;

  /** 任务异常扣分系数 */
  private static final int ERROR_SCORE_MULTIPLIER = 2;

  /** 最近失败扣分上限 */
  private static final int FAILURE_SCORE_MAX_DEDUCTION = 15;

  /** 最近失败扣分系数 */
  private static final int FAILURE_SCORE_MULTIPLIER = 3;

  /** 字节到 MB 的转换因子（1024 * 1024） */
  private static final long BYTES_PER_MB = 1024L * 1024L;

  private final JobRepository jobRepository;
  private final JobLogRepository jobLogRepository;
  private final JobDagInstanceRepository jobDagInstanceRepository;
  private final CronjobProperties cronjobProperties;
  private final ObjectProvider<DefaultTaskDispatcher> taskDispatcherProvider;
  private final ObjectProvider<RunningTaskCounter> runningTaskCounterProvider;
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
  private final ObjectProvider<LeaderElector> leaderElectorProvider;

  private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
  private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();

  /**
   * 获取系统整体健康状态。
   *
   * <p>聚合所有维度的健康指标，返回前端仪表盘所需的完整数据。
   *
   * @return 健康仪表盘数据
   */
  @Operation(summary = "系统整体健康状态")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping
  public YdszResponse<Map<String, Object>> getHealth() {
    Map<String, Object> health = new LinkedHashMap<>(16);

    // 1. 基本信息
    health.put("timestamp", LocalDateTime.now().toString());
    health.put("nodeId", getNodeId());
    health.put("uptime", getUptime());

    // 2. 系统资源
    health.put("system", getSystemHealth());

    // 3. 任务概览
    health.put("tasks", getTaskHealth());

    // 4. DAG 工作流
    health.put("dag", getDagHealth());

    // 5. 调度器状态
    health.put("scheduler", getSchedulerHealth());

    // 6. 最近异常
    Map<String, Object> recentIssues = getRecentIssues();
    health.put("recentIssues", recentIssues);

    // 7. 综合健康评分（从 health Map 获取已解析的 system/tasks Map）
    @SuppressWarnings("unchecked") // @SuppressWarnings 保留原因：泛型擦除，Map<String, Object> 取值后向下转型，编译期无法验证泛型类型
    Map<String, Object> system = (Map<String, Object>) health.get("system");
    @SuppressWarnings("unchecked") // @SuppressWarnings 保留原因：泛型擦除，Map<String, Object> 取值后向下转型，编译期无法验证泛型类型
    Map<String, Object> tasks = (Map<String, Object>) health.get("tasks");
    int overallScore = calculateOverallScore(system, tasks, recentIssues);
    health.put("overallScore", overallScore);

    return YdszResponse.success(health);
  }

  /**
   * 获取系统资源健康状态。
   *
   * @return 系统资源指标
   */
  private Map<String, Object> getSystemHealth() {
    Map<String, Object> system = new HashMap<>(MAP_CAPACITY_8);

    // CPU 使用率
    double cpuUsage = getCpuUsage();
    system.put("cpuUsage", String.format("%.1f%%", cpuUsage));
    system.put("cpuCores", osMXBean.getAvailableProcessors());

    // 内存使用率
    double memUsage = getMemoryUsage();
    system.put("memoryUsage", String.format("%.1f%%", memUsage));
    system.put("memoryUsedMB", getMemoryUsedMB());
    system.put("memoryMaxMB", getMemoryMaxMB());

    // 线程池状态
    Map<String, Object> threadPool = getThreadPoolHealth();
    system.put("threadPool", threadPool);

    // 健康级别
    String healthLevel = "HEALTHY";
    if (cpuUsage > POOL_USAGE_WARN_THRESHOLD || memUsage > MEMORY_USAGE_WARN_THRESHOLD
        || threadPool.containsKey("usagePct")
        && (int) threadPool.get("usagePct") > POOL_USAGE_WARN_THRESHOLD) {
      healthLevel = "WARNING";
    }
    if (cpuUsage > CRITICAL_USAGE_THRESHOLD || memUsage > CRITICAL_USAGE_THRESHOLD) {
      healthLevel = "CRITICAL";
    }
    system.put("healthLevel", healthLevel);

    return system;
  }

  /**
   * 获取任务健康概览。
   *
   * @return 任务统计
   */
  private Map<String, Object> getTaskHealth() {
    Map<String, Object> tasks = new HashMap<>(MAP_CAPACITY_8);

    // 任务状态分布
    long total = jobRepository.countAll();
    long normal = jobRepository.countByStatus("NORMAL");
    long paused = jobRepository.countByStatus("PAUSED");
    long error = jobRepository.countByStatus("ERROR");
    long autoPaused = jobRepository.countByStatus("AUTO_PAUSED");

    tasks.put("total", total);
    tasks.put("normal", normal);
    tasks.put("paused", paused);
    tasks.put("error", error);
    tasks.put("autoPaused", autoPaused);

    // 今日执行统计
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    long todayTotal = jobLogRepository.countByStatusAfter(null, todayStart);
    long todaySuccess = jobLogRepository.countByStatusAfter("SUCCESS", todayStart);
    long todayFailed = jobLogRepository.countByStatusAfter("FAILED", todayStart);
    long todayRunning = jobLogRepository.countByStatusAfter("RUNNING", null);

    Map<String, Object> todayExec = new HashMap<>(MAP_CAPACITY_4);
    todayExec.put("total", todayTotal);
    todayExec.put("success", todaySuccess);
    todayExec.put("failed", todayFailed);
    todayExec.put("running", todayRunning);
    todayExec.put("successRate",
        todayTotal > 0 ? String.format("%.1f%%", todaySuccess * 100.0 / todayTotal) : "N/A");
    tasks.put("todayExecution", todayExec);

    return tasks;
  }

  /**
   * 获取 DAG 工作流健康状态。
   *
   * @return DAG 统计
   */
  private Map<String, Object> getDagHealth() {
    Map<String, Object> dag = new HashMap<>(MAP_CAPACITY_4);

    // 运行中实例数
    long runningInstances = jobDagInstanceRepository.countByStatus("RUNNING");
    long todayInstances = jobDagInstanceRepository.countByDate(LocalDate.now());
    long todaySuccessInstances = jobDagInstanceRepository.countByStatusAndDate("SUCCESS", LocalDate.now());
    long todayFailedInstances = jobDagInstanceRepository.countByStatusAndDate("FAILED", LocalDate.now());

    dag.put("runningInstances", runningInstances);
    dag.put("todayTotal", todayInstances);
    dag.put("todaySuccess", todaySuccessInstances);
    dag.put("todayFailed", todayFailedInstances);
    dag.put("todaySuccessRate",
        todayInstances > 0 ? String.format("%.1f%%", todaySuccessInstances * 100.0 / todayInstances) : "N/A");

    return dag;
  }

  /**
   * 获取调度器健康状态。
   *
   * @return 调度器指标
   */
  private Map<String, Object> getSchedulerHealth() {
    Map<String, Object> scheduler = new HashMap<>(MAP_CAPACITY_4);

    // Leader 状态
    scheduler.put("leaderEnabled", cronjobProperties.getLeader().isEnabled());
    LeaderElector leaderElector = leaderElectorProvider.getIfAvailable();
    if (leaderElector != null) {
      scheduler.put("isLeader", leaderElector.isLeader(LEADER_ROLE));
      scheduler.put("currentLeader", leaderElector.getCurrentLeader(LEADER_ROLE));
    }

    // 运行中任务数
    RunningTaskCounter counter = runningTaskCounterProvider.getIfAvailable();
    long runningTasks = counter != null ? counter.getCount() : 0;
    scheduler.put("clusterRunningTasks", runningTasks);

    // 系统负载评分
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics != null) {
      scheduler.put("systemLoadScore", CronjobMetrics.getSystemLoadScore());
    }

    // 配置摘要
    Map<String, Object> config = new HashMap<>(MAP_CAPACITY_4);
    config.put("maxConcurrent", cronjobProperties.getExecutor().getMaxConcurrent());
    scheduler.put("config", config);

    return scheduler;
  }

  /**
   * 获取最近异常列表。
   *
   * @return 异常信息
   */
  private Map<String, Object> getRecentIssues() {
    Map<String, Object> issues = new HashMap<>(MAP_CAPACITY_4);

    // 最近失败任务
    List<JobLogVO> recentFailures = jobLogRepository.findRecentFailures(RECENT_FAILURES_LIMIT);
    issues.put("recentFailures", recentFailures);
    issues.put("failureCount", recentFailures.size());

    return issues;
  }

  /**
   * 获取线程池健康状态。
   *
   * @return 线程池指标
   */
  private Map<String, Object> getThreadPoolHealth() {
    Map<String, Object> pool = new HashMap<>(MAP_CAPACITY_4);
    DefaultTaskDispatcher dispatcher = taskDispatcherProvider.getIfAvailable();
    if (dispatcher == null) {
      return pool;
    }
    ThreadPoolExecutor executor = dispatcher.getTaskExecutorPool();
    if (executor == null) {
      return pool;
    }

    int activeCount = executor.getActiveCount();
    int poolSize = executor.getPoolSize();
    int maxPoolSize = executor.getMaximumPoolSize();
    int queueSize = executor.getQueue().size();

    pool.put("activeCount", activeCount);
    pool.put("poolSize", poolSize);
    pool.put("maxPoolSize", maxPoolSize);
    pool.put("queueSize", queueSize);
    pool.put("usagePct", maxPoolSize > 0 ? (int) ((double) activeCount / maxPoolSize * 100) : 0);

    return pool;
  }

  /**
   * 计算综合健康评分（0-100）。
   *
   * <p>采用强类型参数接收各维度健康数据，避免从泛型 Map 中取值时的 unchecked 强转，
   * 同时提升方法可读性和编译期类型安全。
   *
   * @param system 系统资源健康数据
   * @param tasks 任务健康数据
   * @param issues 最近异常数据
   * @return 综合评分
   */
  private int calculateOverallScore(Map<String, Object> system,
      Map<String, Object> tasks, Map<String, Object> issues) {
    int score = 100;

    // 系统资源扣分
    String healthLevel = (String) system.get("healthLevel");
    if ("WARNING".equals(healthLevel)) {
      score -= WARNING_SCORE_DEDUCTION;
    } else if ("CRITICAL".equals(healthLevel)) {
      score -= CRITICAL_SCORE_DEDUCTION;
    }

    // 任务异常扣分
    long error = ((Number) tasks.getOrDefault("error", 0L)).longValue();
    if (error > 0) {
      score -= Math.min(ERROR_SCORE_MAX_DEDUCTION, error * ERROR_SCORE_MULTIPLIER);
    }

    // 最近失败扣分
    int failureCount = ((Number) issues.getOrDefault("failureCount", 0)).intValue();
    if (failureCount > 0) {
      score -= Math.min(FAILURE_SCORE_MAX_DEDUCTION, failureCount * FAILURE_SCORE_MULTIPLIER);
    }

    return Math.max(0, score);
  }

  /**
   * 获取 CPU 使用率（百分比）。
   */
  private double getCpuUsage() {
    try {
      // FQN-OK: name conflict with OperatingSystemMXBean
      if (osMXBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
        double load = sunOs.getCpuLoad();
        return load >= 0 ? load * 100 : 0;
      }
    } catch (Exception ignored) {
      // 降级处理
    }
    return 0;
  }

  /**
   * 获取堆内存使用率（百分比）。
   */
  private double getMemoryUsage() {
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
   * 获取已用堆内存（MB）。
   */
  private long getMemoryUsedMB() {
    return memoryMXBean.getHeapMemoryUsage().getUsed() / BYTES_PER_MB;
  }

  /**
   * 获取最大堆内存（MB）。
   */
  private long getMemoryMaxMB() {
    return memoryMXBean.getHeapMemoryUsage().getMax() / BYTES_PER_MB;
  }

  /**
   * 获取当前节点标识。
   */
  private String getNodeId() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }

  /**
   * 获取系统运行时间（毫秒）。
   */
  private long getUptime() {
    return ManagementFactory.getRuntimeMXBean().getUptime();
  }
}
