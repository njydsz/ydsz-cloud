package com.njydsz.cronjob.server.core.dispatch;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.cronjob.infra.entity.job.Job;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.core.leader.PartitionLeaderManager;
import com.njydsz.cronjob.server.core.scheduler.CalendarScheduleFilter;
import com.njydsz.cronjob.server.core.scheduler.NextFireTimeCalculator;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 任务扫描器（P1-7 Leader 模式专用）。
 *
 * <p>仅当 {@code ydsz.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。 定时（默认 5s）扫描 {@code ydsz_job} 表中
 * {@code next_fire_time <= NOW()} 的任务， 通过 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占式行锁获取待派发任务，
 * 然后调用 {@link TaskDispatcher#dispatch(Job, String, String)} 派发到执行节点。
 *
 * <h3>执行流程</h3>
 *
 * <ol>
 *   <li>检查 Leader 身份（非 Leader 节点直接返回，避免重复扫描）
 *   <li>开启事务，调用 {@link JobMapper#selectDueJobs(LocalDateTime, int)} 抢占式扫描
 *   <li>对每个任务 CAS 推进 {@code next_fire_time}（防止 Leader 切换时重复派发）
 *   <li>提交事务后调用 {@link TaskDispatcher} 派发（避免长事务阻塞）
 *   <li>派发结果（成功/失败/跳过）记录到日志
 * </ol>
 *
 * <p><b>避免重复派发的设计</b>：
 *
 * <ul>
 *   <li>DB 行锁：{@code FOR UPDATE SKIP LOCKED} 保证多个 Leader 候选节点互不冲突
 *   <li>CAS 推进：{@code WHERE next_fire_time = #{oldNextFireTime}} 保证 Leader 切换时不重复
 *   <li>Redis 任务锁：{@link TaskDispatcher} 内部的 {@code ydsz:job:lock:*} 锁兜底
 * </ul>
 *
 * <p><b>故障转移</b>：Leader 节点宕机后，lease 到期自动释放，其他节点竞选为新 Leader， 新 Leader 扫描时会重新发现 {@code next_fire_time
 * <= NOW()} 的任务并派发。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class JobScanner {

  private final JobTransactionService jobTransactionService;
  private final LeaderElector leaderElector;
  private final TaskDispatcher taskDispatcher;
  private final CronjobProperties cronjobProperties;

  /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

  /** P2-9: 分区 Leader 管理器（可选注入，仅分区调度启用时存在） */
  private final ObjectProvider<PartitionLeaderManager> partitionLeaderManagerProvider;

  /** P0-7b: 日历调度过滤器（可选注入，启用时按工作日/节假日过滤派发） */
  private final ObjectProvider<CalendarScheduleFilter> calendarScheduleFilterProvider;

  /** P1-3: 下次触发时间计算器（统一 cron 解析与时区处理） */
  private final NextFireTimeCalculator nextFireTimeCalculator;

  private final ApplicationContext applicationContext;

  /** 扫描执行中标志（避免上次扫描未完成时重叠触发） */
  private final AtomicBoolean scanning = new AtomicBoolean(false);

  /** Leader 角色（从配置读取，便于多套调度集群隔离） */
  private String leaderRole;

  /** P0-2: 并行派发线程池 */
  private ExecutorService dispatchPool;

  /** P1-8: 是否使用外部线程池（true=common-thread 管理，不负责关闭） */
  private boolean useExternalDispatchPool = false;

  /**
   * 初始化扫描器：解析 Leader 角色，并决策并行派发线程池来源。
   *
   * <p>仅在 {@code ydsz.cronjob.leader.enabled=true} 时进入派发相关初始化； 否则仅记录 Leaderless 模式日志、不创建任何线程池。
   * 当开启并行派发（{@code parallelDispatchEnabled=true}）时：
   *
   * <ul>
   *   <li>优先复用 common-thread 统一托管的 {@code cronjobDispatchExecutor} 线程池， 此时 {@code
   *       useExternalDispatchPool=true}，生命周期由 common-thread 负责，本类不关闭；
   *   <li>若从 Spring 容器获取失败（该 Bean 未注册），则回退自建守护线程池， 此时 {@code useExternalDispatchPool=false}，由
   *       {@link #shutdown()} 负责关闭。
   * </ul>
   *
   * 非并行派发模式下不创建线程池，走串行派发路径。
   */
  @PostConstruct
  public void init() {
    this.leaderRole = cronjobProperties.getLeader().getRole();
    if (cronjobProperties.getLeader().isEnabled()) {
      // P1-8: 优先使用 common-thread 统一管理的 cronjobDispatchExecutor 线程池
      if (cronjobProperties.getScanner().isParallelDispatchEnabled()) {
        try {
          ThreadPoolTaskExecutor threadPool =
              applicationContext.getBean("cronjobDispatchExecutor", ThreadPoolTaskExecutor.class);
          this.dispatchPool = threadPool.getThreadPoolExecutor();
          this.useExternalDispatchPool = true;
          log.info(
              "[JobScanner] 初始化完成, role={} scanInterval={}ms batchSize={} parallelDispatch=true pool=common-thread(cronjobDispatchExecutor)",
              leaderRole,
              cronjobProperties.getScanner().getIntervalMs(),
              cronjobProperties.getScanner().getBatchSize());
        } catch (Exception e) {
          int poolSize = cronjobProperties.getScanner().getParallelDispatchPoolSize();
          // 使用 common-thread ExecutorUtils 创建降级线程池（符合云顶规范 15.4）
          this.dispatchPool =
              ExecutorUtils.builder()
                  .corePoolSize(poolSize)
                  .maxPoolSize(poolSize)
                  .queueCapacity(1024)
                  .threadNamePrefix("job-scanner-dispatch-")
                  .daemon(true)
                  .build();
          this.useExternalDispatchPool = false;
          log.info(
              "[JobScanner] 初始化完成, role={} scanInterval={}ms batchSize={} parallelDispatch=true poolSize={} (manual fallback)",
              leaderRole,
              cronjobProperties.getScanner().getIntervalMs(),
              cronjobProperties.getScanner().getBatchSize(),
              poolSize);
        }
      } else {
        log.info(
            "[JobScanner] 初始化完成, role={} scanInterval={}ms batchSize={} parallelDispatch=false",
            leaderRole,
            cronjobProperties.getScanner().getIntervalMs(),
            cronjobProperties.getScanner().getBatchSize());
      }
    } else {
      log.info("[JobScanner] leader.enabled=false, 扫描器不启用（Leaderless 模式）");
    }
  }

  /**
   * 容器销毁钩子（预留）。
   *
   * <p>本方法保持空实现：当并行派发复用 common-thread 托管的 {@code cronjobDispatchExecutor} 时（{@code
   * useExternalDispatchPool=true}）， 线程池生命周期由 common-thread 统一回收，本类不应手动关闭以免误伤共享池。
   * 仅在回退自建线程池的场景下，实际的线程池关闭逻辑统一收敛在 {@link #shutdown()} 中， 避免两处销毁逻辑重复执行。
   */
  @PreDestroy
  public void destroy() {
    // P0-3: dispatchPool 由 common-thread 管理生命周期，无需手动关闭
  }

  /**
   * 定时扫描待触发任务。
   *
   * <p>使用 {@code fixedDelayString} 而非 {@code fixedRateString}， 避免上次扫描耗时较长时任务堆积。
   */
  @Scheduled(fixedDelayString = "${ydsz.cronjob.scanner.interval-ms:5000}")
  public void scan() {
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }
    if (!scanning.compareAndSet(false, true)) {
      log.debug("[JobScanner] 上次扫描尚未完成, 跳过本次执行");
      return;
    }
    // P6-2: 更新扫描中状态指标
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.setScanning(true);
    }
    try {
      doScan();
    } catch (Exception e) {
      log.error("[JobScanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
    } finally {
      scanning.set(false);
      // P6-2: 更新扫描中状态指标
      if (metrics != null) {
        metrics.setScanning(false);
      }
    }
  }

  /**
   * 执行一次扫描（事务内抢占 + CAS 推进 + 事务外派发）。
   *
   * <p>P2-2: 在派发前先判定 Misfire：
   *
   * <ul>
   *   <li>{@link MisfirePolicy#SKIP} 跳过本次错过的触发，仅推进 next_fire_time
   *   <li>{@link MisfirePolicy#FIRE_NOW} 立即执行一次（默认）
   *   <li>{@link MisfirePolicy#COALESCE} 执行一次，日志 triggerType 标记 MISFIRED
   * </ul>
   *
   * <p>P6-1: 在派发前通过 {@link TracerUtils#getOrCreate()} 初始化 traceId 到 MDC， 使 DefaultTaskDispatcher 写入
   * job_log.trace_id 时能取到非空值， 实现"扫描 → 派发 → 执行 → 日志"全链路 traceId 串联。 单个任务派发完成后立即清理 MDC，避免 traceId
   * 串任务。
   */
  private void doScan() {
    LocalDateTime now = LocalDateTime.now();
    int batchSize = cronjobProperties.getScanner().getBatchSize();
    List<Job> dueJobs = jobTransactionService.acquireDueJobs(now, batchSize);
    // P6-2: 更新上次扫描到的待触发任务数指标
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.setLastScanDueJobs(dueJobs.size());
    }
    if (dueJobs.isEmpty()) {
      return;
    }
    log.info("[JobScanner] 扫描到 {} 个待触发任务: role={}", dueJobs.size(), leaderRole);

    // P0-2: 并行派发模式
    if (cronjobProperties.getScanner().isParallelDispatchEnabled() && dispatchPool != null) {
      doParallelDispatch(dueJobs, now, metrics);
    } else {
      doSequentialDispatch(dueJobs, now, metrics);
    }
  }

  /**
   * P0-2: 并行派发待触发任务。
   *
   * <p>每个任务的 Misfire 判定 + CAS 推进 + dispatch 在独立线程中执行， CAS 操作（WHERE next_fire_time =
   * old）保证幂等，并行不会导致重复派发。 使用 CountDownLatch 等待全部完成后返回，确保单次扫描内不遗漏。
   *
   * @param dueJobs 待触发任务列表
   * @param now 扫描时间
   * @param metrics 指标收集器（可空）
   */
  private void doParallelDispatch(List<Job> dueJobs, LocalDateTime now, CronjobMetrics metrics) {
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger skipCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);
    List<CompletableFuture<Void>> futures = new ArrayList<>(dueJobs.size());
    for (Job job : dueJobs) {
      CompletableFuture<Void> f =
          CompletableFuture.runAsync(
              () -> dispatchSingleJob(job, now, metrics, successCount, skipCount, failCount),
              dispatchPool);
      futures.add(f);
    }
    // 等待全部完成，任一异常不影响其他任务
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    log.info(
        "[JobScanner] 并行派发完成: total={} success={} skip={} fail={}",
        dueJobs.size(),
        successCount.get(),
        skipCount.get(),
        failCount.get());
  }

  /** P0-2: 串行派发（兼容模式，parallelDispatchEnabled=false 时使用）。 */
  private void doSequentialDispatch(List<Job> dueJobs, LocalDateTime now, CronjobMetrics metrics) {
    for (Job job : dueJobs) {
      dispatchSingleJob(job, now, metrics, null, null, null);
    }
  }

  /**
   * P0-2: 派发单个任务（Misfire 判定 + CAS 推进 + dispatch）。
   *
   * <p>提取公共逻辑，串行/并行模式共用。每个任务独立生成 traceId， 异常不传播到外层，仅记录日志并递增计数器。
   */
  private void dispatchSingleJob(
      Job job,
      LocalDateTime now,
      CronjobMetrics metrics,
      AtomicInteger successCount,
      AtomicInteger skipCount,
      AtomicInteger failCount) {
    // P2-9: 分区调度过滤 — 非本节点分区的任务跳过
    PartitionLeaderManager partitionManager = partitionLeaderManagerProvider.getIfAvailable();
    if (partitionManager != null && !partitionManager.isMyPartition(job)) {
      log.debug(
          "[JobScanner] 任务不属于本节点分区, 跳过: key={} partition={}",
          job.getJobKey(),
          partitionManager.computePartition(job));
      if (skipCount != null) skipCount.incrementAndGet();
      return;
    }
    // P6-1: 为每个任务派发生成独立 traceId，保证任务间链路隔离
    TracerUtils.getOrCreateTraceId();
    try {
      // P0-7b: 日历调度过滤 — 按工作日/节假日过滤派发
      CalendarScheduleFilter calendarFilter = calendarScheduleFilterProvider.getIfAvailable();
      if (calendarFilter != null) {
        String calendarType = calendarFilter.parseCalendarType(job.getParamsJson());
        Set<LocalDate> holidays = calendarFilter.parseHolidays(job.getParamsJson());
        if (!calendarFilter.shouldExecute(calendarType, holidays, LocalDate.now())) {
          // 日历过滤跳过：仅推进 next_fire_time，不派发
          LocalDateTime newNext = nextFireTime(job);
          boolean advanced =
              jobTransactionService.advanceNextFireTime(job, job.getNextFireTime(), newNext, now);
          if (metrics != null) {
            metrics.incMisfire("CALENDAR_SKIP");
          }
          log.info(
              "[JobScanner] 日历过滤跳过派发: key={} calendarType={} advanced={}",
              job.getJobKey(),
              calendarType,
              advanced);
          if (skipCount != null) skipCount.incrementAndGet();
          return;
        }
      }
      // P2-2: Misfire 判定
      MisfirePolicy policy = MisfirePolicy.parse(job.getMisfirePolicy());
      boolean misfired = isMisfired(job, now);
      if (misfired && policy == MisfirePolicy.SKIP) {
        // 仅推进 next_fire_time，不派发
        LocalDateTime newNext = nextFireTime(job);
        boolean advanced =
            jobTransactionService.advanceNextFireTime(job, job.getNextFireTime(), newNext, now);
        if (metrics != null) {
          metrics.incMisfire("SKIP");
        }
        log.info("[JobScanner] Misfire SKIP 跳过派发: key={} advanced={}", job.getJobKey(), advanced);
        if (skipCount != null) skipCount.incrementAndGet();
        return;
      }
      // 计算新的 next_fire_time 并 CAS 推进
      LocalDateTime oldNext = job.getNextFireTime();
      LocalDateTime newNext = nextFireTime(job);
      boolean advanced = jobTransactionService.advanceNextFireTime(job, oldNext, newNext, now);
      if (!advanced) {
        log.debug("[JobScanner] 任务 next_fire_time 已被其他节点推进, 跳过: key={}", job.getJobKey());
        if (skipCount != null) skipCount.incrementAndGet();
        return;
      }
      // P2-2: 选择 triggerType
      String triggerType = DefaultTaskDispatcher.TRIGGER_CRON;
      if (misfired && policy == MisfirePolicy.COALESCE) {
        triggerType = DefaultTaskDispatcher.TRIGGER_MISFIRED;
        if (metrics != null) {
          metrics.incMisfire("COALESCE");
        }
        log.info("[JobScanner] Misfire COALESCE 派发（日志标记 MISFIRED）: key={}", job.getJobKey());
      } else if (misfired) {
        if (metrics != null) {
          metrics.incMisfire("FIRE_NOW");
        }
        log.info("[JobScanner] Misfire FIRE_NOW 立即派发: key={}", job.getJobKey());
      }
      String logId = taskDispatcher.dispatch(job, null, triggerType);
      if (logId == null) {
        log.debug("[JobScanner] 任务异步派发或被跳过: key={} triggerType={}", job.getJobKey(), triggerType);
      } else {
        log.info(
            "[JobScanner] 任务派发成功: key={} logId={} triggerType={} traceId={}",
            job.getJobKey(),
            logId,
            triggerType,
            TracerUtils.getTraceId());
      }
      if (successCount != null) successCount.incrementAndGet();
    } catch (Exception e) {
      log.error("[JobScanner] 任务派发失败: key={} reason={}", job.getJobKey(), e.getMessage(), e);
      if (failCount != null) failCount.incrementAndGet();
    } finally {
      // P6-1: 清理 MDC，避免 traceId 串到下一个任务
      TracerUtils.clear();
    }
  }

  /**
   * 判定任务是否 Misfire。
   *
   * <p>当 {@code next_fire_time} 早于 {@code NOW() - misfireGraceMinutes} 时视为 Misfire。
   *
   * @param job 任务定义
   * @param now 当前时间
   * @return true 视为 Misfire
   */
  private boolean isMisfired(Job job, LocalDateTime now) {
    if (job.getNextFireTime() == null) {
      return false;
    }
    Duration grace = Duration.ofMinutes(cronjobProperties.getScanner().getMisfireGraceMinutes());
    LocalDateTime threshold = now.minus(grace);
    return job.getNextFireTime().isBefore(threshold);
  }

  /**
   * 计算下次触发时间（统一委托 {@link NextFireTimeCalculator}，支持任务级时区）。
   *
   * @param job 任务定义（含 cron 表达式与时区）
   * @return 下次触发时间；解析失败时返回 null
   */
  private LocalDateTime nextFireTime(Job job) {
    return nextFireTimeCalculator.calculate(job);
  }

  /**
   * 定时清理过期的 cron 缓存条目（每 5 分钟执行一次）。
   *
   * <p>防止长期运行后缓存堆积不再使用的 cron 表达式。
   */
  @Scheduled(fixedDelay = 300_000L)
  public void cleanupCronCache() {
    nextFireTimeCalculator.cleanup();
  }

  /** 优雅下线：无需特殊处理，{@link LeaderElector#release(String)} 会释放 Leader 锁。 */
  @PreDestroy
  public void shutdown() {
    log.info("[JobScanner] 关闭: role={}", leaderRole);
    // P0-2: 关闭并行派发线程池
    if (dispatchPool != null && !dispatchPool.isShutdown()) {
      dispatchPool.shutdown();
      log.info("[JobScanner] 并行派发线程池已关闭");
    }
  }

  /** 暴露扫描中状态（仅供测试断言使用）。 */
  boolean isScanning() {
    return scanning.get();
  }

  /** 暴露 Leader 角色（仅供测试断言使用）。 */
  String getLeaderRole() {
    return leaderRole;
  }

  /** 计算任务 Misfire 宽容窗口（仅供测试断言使用）。 */
  Duration getMisfireGrace() {
    return Duration.ofMinutes(cronjobProperties.getScanner().getMisfireGraceMinutes());
  }
}
