package com.njydsz.cronjob.server.core.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.PrecisionConfig;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.dispatch.JobTransactionService;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

/**
 * P1-P3: 秒级预读调度器（轻量时间轮，提升 CRON 任务调度精度）。
 *
 * <p>对标 XXL-Job 的 6000ms 预读窗口与 PowerJob 的时间轮设计：
 *
 * <ul>
 *   <li><b>现状</b>：JobScanner 每 5s 扫表一次，CRON 任务最差延迟 {@code 5s + 派发延迟}，无法支撑秒级精度
 *   <li><b>方案</b>：本调度器由 Leader 每 {@code scanIntervalMs}（默认 3s）预读
 *       {@code next_fire_time ∈ [now, now + windowSeconds]} 的 CRON 任务，注册到内存
 *       {@code ScheduledExecutorService}，到期精确触发（毫秒级精度）
 *   <li><b>兜底</b>：主扫描器 JobScanner 保持不变（负责到期任务 + FIXED_RATE/FIXED_DELAY/API 类型），
 *       预读调度器仅处理 CRON 窗口任务；CAS 推进失败时自动让位主扫描器，无重复派发风险
 * </ul>
 *
 * <h3>防重复派发</h3>
 *
 * <p>触发前通过 {@link JobTransactionService#advanceNextFireTime} CAS 推进 next_fire_time
 * （{@code WHERE next_fire_time = old}），与 JobScanner 共享同一原子语义：
 *
 * <ul>
 *   <li>预读先到：CAS 成功 → 派发；JobScanner 稍后扫到已推进的任务 → 跳过
 *   <li>扫描先到：预读触发时 CAS 失败 → 跳过（由扫描器完成派发）
 * </ul>
 *
 * <p>通过 {@code ydsz.cronjob.preload.enabled=true} 启用（默认关闭，保守策略）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class TaskPreloadScheduler {

  private final JobTransactionService jobTransactionService;
  private final TaskDispatcher taskDispatcher;
  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;
  private final NextFireTimeCalculator nextFireTimeCalculator;

  /** 内存精准触发线程池（单线程，daemon） */
  private ScheduledExecutorService precisionScheduler;

  /** 已注册的预读任务: jobId → ScheduledFuture（防止重复注册） */
  private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingJobs = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    this.precisionScheduler = ExecutorUtils.newScheduledThreadPool(1, "job-preload-");
    PrecisionConfig cfg = cronjobProperties.getPreload();
    log.info(
        "[Preload] 秒级预读调度器初始化: enabled={} scanInterval={}ms window={}s batch={}",
        cfg.isEnabled(),
        cfg.getScanIntervalMs(),
        cfg.getWindowSeconds(),
        cfg.getBatchSize());
  }

  @PreDestroy
  public void shutdown() {
    pendingJobs.values().forEach(future -> future.cancel(false));
    pendingJobs.clear();
    if (precisionScheduler != null) {
      precisionScheduler.shutdownNow();
    }
  }

  /**
   * 定时预读窗口内到期的 CRON 任务并注册精准触发。
   *
   * <p>仅 Leader 执行；未启用或非 Leader 时直接返回。
   */
  @Scheduled(fixedDelayString = "${ydsz.cronjob.preload.scan-interval-ms:3000}")
  public void preloadScan() {
    PrecisionConfig cfg = cronjobProperties.getPreload();
    if (!cfg.isEnabled()) {
      return;
    }
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    if (!leaderElector.isLeader(cronjobProperties.getLeader().getRole())) {
      return;
    }
    try {
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime windowEnd = now.plusSeconds(cfg.getWindowSeconds());
      List<JobVO> dueJobs =
          jobTransactionService.acquireDueJobsInWindow(now, windowEnd, cfg.getBatchSize());
      if (dueJobs.isEmpty()) {
        return;
      }
      for (JobVO job : dueJobs) {
        if (job.getNextFireTime() == null || pendingJobs.containsKey(job.getId())) {
          continue;
        }
        schedulePreciseFire(job, now);
      }
    } catch (Exception e) {
      log.warn("[Preload] 预读扫描异常(交由主扫描器兜底): reason={}", e.getMessage());
    }
  }

  /** 将任务注册到内存调度器，到期精确触发。 */
  private void schedulePreciseFire(JobVO job, LocalDateTime now) {
    long delayMs = Math.max(0, Duration.between(now, job.getNextFireTime()).toMillis());
    try {
      ScheduledFuture<?> future =
          precisionScheduler.schedule(() -> fireJob(job), delayMs, TimeUnit.MILLISECONDS);
      pendingJobs.put(job.getId(), future);
      log.debug(
          "[Preload] 注册秒级触发: key={} nextFire={} delay={}ms",
          job.getJobKey(),
          job.getNextFireTime(),
          delayMs);
    } catch (RejectedExecutionException e) {
      log.warn("[Preload] 预读调度器已关闭, 交由主扫描器兜底: key={}", job.getJobKey());
    }
  }

  /** 精准触发：CAS 推进 next_fire_time 后派发（CAS 失败说明已被主扫描器处理，跳过）。 */
  private void fireJob(JobVO job) {
    pendingJobs.remove(job.getId());
    try {
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime oldNext = job.getNextFireTime();
      LocalDateTime newNext = nextFireTimeCalculator.calculate(job);
      if (newNext == null) {
        // 表达式非法等极端场景：推进一个固定间隔避免无限重试
        newNext = oldNext.plusMinutes(1);
      }
      boolean advanced = jobTransactionService.advanceNextFireTime(job, oldNext, newNext, now);
      if (!advanced) {
        log.debug("[Preload] CAS 推进失败(已被主扫描器处理), 跳过: key={}", job.getJobKey());
        return;
      }
      taskDispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);
    } catch (Exception e) {
      log.warn(
          "[Preload] 秒级派发失败(交由主扫描器兜底): key={} reason={}", job.getJobKey(), e.getMessage());
    }
  }
}
