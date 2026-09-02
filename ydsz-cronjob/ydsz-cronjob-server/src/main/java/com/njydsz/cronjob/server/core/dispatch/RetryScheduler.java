package com.njydsz.cronjob.server.core.dispatch;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.core.config.CronjobThreadPoolRegistry;

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
 * @since 26.09.01
 */
@Slf4j
@Component
public class RetryScheduler {
  /** 重试延迟封顶（毫秒）：5 分钟 */
  private static final long MAX_DELAY_MILLIS = 300_000L;


  /** 线程池注册表（可选注入，standalone 模式下可能不可用） */
  private final ObjectProvider<CronjobThreadPoolRegistry> registryProvider;

  /**
   * 重试调度线程池（固定2线程，守护线程，CallerRunsPolicy 自然背压）。
   *
   * <p>使用 common-thread ExecutorUtils 统一管理（符合云顶规范 15.4）。
   */
  private final ScheduledExecutorService retryScheduler =
      ExecutorUtils.newScheduledThreadPool(2, "job-retry-");

  /**
   * 构造函数：注入线程池注册表。
   *
   * @param registryProvider 线程池注册表提供者（延迟加载）
   */
  public RetryScheduler(ObjectProvider<CronjobThreadPoolRegistry> registryProvider) {
    this.registryProvider = registryProvider;
  }

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
      JobVO job,
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
  private long calculateDelayMs(JobVO job, int retryCount) {
    Long interval = job.getRetryIntervalMs();
    if (interval == null || interval <= 0) {
      return 0;
    }
    if ("EXPONENTIAL".equals(job.getRetryBackoff())) {
      long delay = interval * (1L << Math.min(retryCount, 10));
      return Math.min(delay, MAX_DELAY_MILLIS);
    }
    return interval;
  }

  /**
   * 初始化：注册重试调度线程池到注册表。
   *
   * <p>注册表可用时注册，支持线程池热更新。
   */
  @PostConstruct
  public void init() {
    CronjobThreadPoolRegistry registry = registryProvider.getIfAvailable();
    if (registry != null && retryScheduler instanceof ThreadPoolExecutor executor) {
      registry.register(CronjobThreadPoolRegistry.RETRY_SCHEDULER, executor);
    }
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
    void execute(JobVO job, boolean holdLock, int retryCount);
  }
}
