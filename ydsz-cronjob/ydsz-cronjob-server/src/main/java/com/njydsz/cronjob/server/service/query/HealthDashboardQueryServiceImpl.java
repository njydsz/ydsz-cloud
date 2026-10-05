package com.njydsz.cronjob.server.service.query;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import com.sun.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.repository.JobDagInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.service.HealthDashboardQueryService;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.executor.RunningTaskCounter;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 健康仪表盘查询 Service 实现（server 层）。
 *
 * <p>聚合系统资源、任务、DAG、调度器多维度运行状态，提供一站式健康检查能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthDashboardQueryServiceImpl implements HealthDashboardQueryService {

  private static final int COLLECTION_CAPACITY = 16;
  private static final int MAP_CAPACITY_16 = 16;
  private static final int MAP_CAPACITY_8 = 8;
  private static final int MAP_CAPACITY_4 = 4;
  private static final int RECENT_FAILURES_LIMIT = 10;
  private static final int POOL_USAGE_WARN_THRESHOLD = 80;
  private static final int MEMORY_USAGE_WARN_THRESHOLD = 85;
  private static final String LEADER_ROLE = "ydsz-job-scheduler";
  private static final int CRITICAL_USAGE_THRESHOLD = 95;
  private static final int WARNING_SCORE_DEDUCTION = 20;
  private static final int CRITICAL_SCORE_DEDUCTION = 50;
  private static final int ERROR_SCORE_MAX_DEDUCTION = 20;
  private static final int ERROR_SCORE_MULTIPLIER = 2;
  private static final int FAILURE_SCORE_MAX_DEDUCTION = 15;
  private static final int FAILURE_SCORE_MULTIPLIER = 3;
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
  private final com.sun.management.OperatingSystemMXBean osMXBean =
      (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

  @Override
  public Map<String, Object> getHealth() {
    Map<String, Object> health = new LinkedHashMap<>(COLLECTION_CAPACITY);

    // 1. 基本信息
    health.put("timestamp", LocalDateTime.now().toString());
    health.put("nodeId", getNodeId());
    health.put("uptime", getUptime());

    // 2. 系统资源
    Map<String, Object> system = getSystemHealth();
    health.put("system", system);

    // 3. 任务概览
    Map<String, Object> tasks = getTaskHealth();
    health.put("tasks", tasks);

    // 4. DAG 工作流
    health.put("dag", getDagHealth());

    // 5. 调度器状态
    health.put("scheduler", getSchedulerHealth());

    // 6. 最近异常
    Map<String, Object> recentIssues = getRecentIssues();
    health.put("recentIssues", recentIssues);

    // 7. 综合健康评分
    int overallScore = calculateOverallScore(system, tasks, recentIssues);
    health.put("overallScore", overallScore);

    return health;
  }

  private Map<String, Object> getSystemHealth() {
    Map<String, Object> system = new HashMap<>(MAP_CAPACITY_8);
    double cpuUsage = getCpuUsage();
    system.put("cpuUsage", String.format("%.1f%%", cpuUsage));
    system.put("cpuCores", osMXBean.getAvailableProcessors());
    double memUsage = getMemoryUsage();
    system.put("memoryUsage", String.format("%.1f%%", memUsage));
    system.put("memoryUsedMB", getMemoryUsedMB());
    system.put("memoryMaxMB", getMemoryMaxMB());
    Map<String, Object> threadPool = getThreadPoolHealth();
    system.put("threadPool", threadPool);
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

  private Map<String, Object> getTaskHealth() {
    Map<String, Object> tasks = new HashMap<>(MAP_CAPACITY_8);
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

  private Map<String, Object> getDagHealth() {
    Map<String, Object> dag = new HashMap<>(MAP_CAPACITY_4);
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

  private Map<String, Object> getSchedulerHealth() {
    Map<String, Object> scheduler = new HashMap<>(MAP_CAPACITY_4);
    scheduler.put("leaderEnabled", cronjobProperties.getLeader().isEnabled());
    LeaderElector leaderElector = leaderElectorProvider.getIfAvailable();
    if (leaderElector != null) {
      scheduler.put("isLeader", leaderElector.isLeader(LEADER_ROLE));
      scheduler.put("currentLeader", leaderElector.getCurrentLeader(LEADER_ROLE));
    }
    RunningTaskCounter counter = runningTaskCounterProvider.getIfAvailable();
    long runningTasks = counter != null ? counter.getCount() : 0;
    scheduler.put("clusterRunningTasks", runningTasks);
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics != null) {
      scheduler.put("systemLoadScore", CronjobMetrics.getSystemLoadScore());
    }
    Map<String, Object> config = new HashMap<>(MAP_CAPACITY_4);
    config.put("maxConcurrent", cronjobProperties.getExecutor().getMaxConcurrent());
    scheduler.put("config", config);
    return scheduler;
  }

  private Map<String, Object> getRecentIssues() {
    Map<String, Object> issues = new HashMap<>(MAP_CAPACITY_4);
    List<JobLogVO> recentFailures = jobLogRepository.findRecentFailures(RECENT_FAILURES_LIMIT);
    issues.put("recentFailures", recentFailures);
    issues.put("failureCount", recentFailures.size());
    return issues;
  }

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

  private int calculateOverallScore(Map<String, Object> system,
      Map<String, Object> tasks, Map<String, Object> issues) {
    int score = 100;
    String healthLevel = (String) system.get("healthLevel");
    if ("WARNING".equals(healthLevel)) {
      score -= WARNING_SCORE_DEDUCTION;
    } else if ("CRITICAL".equals(healthLevel)) {
      score -= CRITICAL_SCORE_DEDUCTION;
    }
    long error = ((Number) tasks.getOrDefault("error", 0L)).longValue();
    if (error > 0) {
      score -= Math.min(ERROR_SCORE_MAX_DEDUCTION, error * ERROR_SCORE_MULTIPLIER);
    }
    int failureCount = ((Number) issues.getOrDefault("failureCount", 0)).intValue();
    if (failureCount > 0) {
      score -= Math.min(FAILURE_SCORE_MAX_DEDUCTION, failureCount * FAILURE_SCORE_MULTIPLIER);
    }
    return Math.max(0, score);
  }

  private double getCpuUsage() {
    try {
      if (osMXBean instanceof OperatingSystemMXBean sunOs) {
        double load = sunOs.getCpuLoad();
        return load >= 0 ? load * 100 : 0;
      }
    } catch (Exception e) {
      log.debug("当前环境不支持此操作，已跳过", e);
    }
    return 0;
  }

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

  private long getMemoryUsedMB() {
    return memoryMXBean.getHeapMemoryUsage().getUsed() / BYTES_PER_MB;
  }

  private long getMemoryMaxMB() {
    return memoryMXBean.getHeapMemoryUsage().getMax() / BYTES_PER_MB;
  }

  private String getNodeId() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }

  private long getUptime() {
    return ManagementFactory.getRuntimeMXBean().getUptime();
  }
}
