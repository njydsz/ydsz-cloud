package com.njydsz.cronjob.server.core.dispatch;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.entity.job.Job;

/**
 * 失败重试调度器（从 DefaultTaskDispatcher 拆分）。
 *
 * <p>负责任务失败后的延迟重试调度：
 *
 * <ul>
 *   <li>根据 retryBackoff 策略（FIXED / EXPONENTIAL）计算重试延迟
 *   <li>通过 ScheduledExecutorService 延迟提交重试任务
 *   <li>支持指数退避（上限 5 分钟）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Component
public class RetryScheduler {

  /** 重试调度线程池 */
  private final ScheduledExecutorService retryScheduler =
      new ScheduledThreadPoolExecutor(
          2,
          r -> {
            Thread t = new Thread(r, "ydsz-job-retry");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.CallerRunsPolicy());

  /**
   * 调度失败重试。
   *
   * <p>当任务执行失败且 maxRetries > 0 且 retryCount < maxRetries 时，通过 ScheduledExecutorService 延迟调度重试。
   * 重试延迟根据 retryBackoff 计算：
   *
   * <ul>
   *   <li>FIXED: 固定 retryIntervalMs
   *   <li>EXPONENTIAL: retryIntervalMs * 2^retryCount
   * </ul>
   *
   * @param job 任务定义
   * @param holdLock 是否持锁
   * @param triggerType 原始触发类型
   * @param retryCount 当前重试次数
   * @param executor 重试执行器（函数式接口，由 DefaultTaskDispatcher 传入）
   */
  public void scheduleRetry(
      Job job,
      boolean holdLock,
      String triggerType,
      int retryCount,
      RetryExecutor executor) {
    Integer maxRetries = job.getMaxRetries();
    if (maxRetries == null || maxRetries <= 0 || retryCount >= maxRetries) {
      return;
    }
    long delayMs = calculateDelayMs(job, retryCount);
    int nextRetry = retryCount + 1;
    log.info(
        "[RetryScheduler] 调度失败重试: key={} retry={}/{} delay={}ms backoff={}",
        job.getJobKey(),
        nextRetry,
        maxRetries,
        delayMs,
        job.getRetryBackoff());
    try {
      retryScheduler.schedule(
          () -> {
            try {
              executor.execute(job, holdLock, nextRetry);
            } catch (Exception e) {
              log.error(
                  "[RetryScheduler] 重试执行异常: key={} retry={} reason={}",
                  job.getJobKey(),
                  nextRetry,
                  e.getMessage(),
                  e);
            }
          },
          delayMs,
          TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      log.error(
          "[RetryScheduler] 调度重试失败: key={} retry={} reason={}",
          job.getJobKey(),
          nextRetry,
          e.getMessage(),
          e);
    }
  }

  /**
   * 计算重试延迟（毫秒）。
   *
   * <p>EXPONENTIAL 模式下延迟 = interval * 2^retryCount，上限 5 分钟避免过长延迟。
   *
   * @param job 任务定义
   * @param retryCount 当前重试次数
   * @return 延迟毫秒数
   */
  private long calculateDelayMs(Job job, int retryCount) {
    Long interval = job.getRetryIntervalMs();
    if (interval == null || interval <= 0) {
      return 0;
    }
    if ("EXPONENTIAL".equals(job.getRetryBackoff())) {
      long delay = interval * (1L << Math.min(retryCount, 10));
      return Math.min(delay, 300_000L);
    }
    return interval;
  }

  /** 优雅关闭重试调度线程池。 */
  @PreDestroy
  public void shutdown() {
    retryScheduler.shutdown();
    log.info("[RetryScheduler] 重试调度线程池已关闭");
  }

  /**
   * 重试执行器接口。
   *
   * <p>的重试任务最终需要回到 DefaultTaskDispatcher.executeJob() 执行，通过此接口解耦 RetryScheduler 与
   * DefaultTaskDispatcher 的直接依赖。
   */
  @FunctionalInterface
  public interface RetryExecutor {
    /**
     * 执行重试任务。
     *
     * @param job 任务定义
     * @param holdLock 是否持锁
     * @param retryCount 当前重试次数
     */
    void execute(Job job, boolean holdLock, int retryCount);
  }
}
