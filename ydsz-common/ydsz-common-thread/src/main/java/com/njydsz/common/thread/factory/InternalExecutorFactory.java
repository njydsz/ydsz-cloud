package com.njydsz.common.thread.factory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * Common 内部模块轻量线程池工厂。
 *
 * <p>为 common 层 L5 业务服务模块（event/queue/search/seata/sentry/audit 等）提供命名化、
 * 可观测、统一配置的轻量线程池工厂。解决各模块各自 {@code new ThreadPoolExecutor} 导致的管理碎片化问题。
 *
 * <p><b>设计定位：</b>
 *
 * <ul>
 *   <li>面向 common 内部模块的<b>轻量级内部池</b>场景（如异步事件处理、定时扫描、批量任务）</li>
 *   <li>与业务模块使用的配置驱动线程池（{@code ydsz.thread.pools.*}）互补：前者面向业务隔离，后者面向内部复用</li>
 *   <li>所有创建的线程池统一使用 {@code ydsz-internal-} 前缀，纳入统一监控和优雅关闭</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 创建定时线程池（用于内部扫描任务）
 * ScheduledExecutorService scheduler = InternalExecutorFactory.newScheduledThreadPool(
 *     "event-scheduler", 2);
 *
 * // 创建固定大小线程池（用于内部异步处理）
 * ExecutorService executor = InternalExecutorFactory.newFixedThreadPool(
 *     "queue-consumer", 4);
 * }</pre>
 *
 * <p><b>规范合规：</b>通过本工厂创建的线程池即视为已纳入统一治理，不触发 checkstyle {@code
 * RegexpSinglelineJava} 线程池构造检测。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public final class InternalExecutorFactory {

  /** 线程名前缀 */
  private static final String THREAD_NAME_PREFIX = "ydsz-internal-";

  /** 池序号生成器 */
  private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);

  /** 默认队列容量 */
  private static final int DEFAULT_QUEUE_CAPACITY = 512;

  /** 默认最大线程数 */
  private static final int DEFAULT_MAX_POOL_SIZE = Math.max(Runtime.getRuntime().availableProcessors() * 2, 16);

  private InternalExecutorFactory() {
    throw new UnsupportedOperationException("InternalExecutorFactory is a utility class");
  }

  /**
   * 创建固定大小线程池（用于内部异步处理）。
   *
   * <p>核心线程数等于最大线程数，空闲线程不回收，使用有界队列防止 OOM。
   *
   * @param poolName 池名称（用于线程命名和监控，如 "event-processor"）
   * @param nThreads 线程数
   * @return 固定大小线程池实例
   */
  public static ExecutorService newFixedThreadPool(String poolName, int nThreads) {
    return newFixedThreadPool(poolName, nThreads, DEFAULT_QUEUE_CAPACITY);
  }

  /**
   * 创建固定大小线程池（自定义队列容量）。
   *
   * @param poolName 池名称
   * @param nThreads 线程数
   * @param queueCapacity 队列容量
   * @return 固定大小线程池实例
   */
  public static ExecutorService newFixedThreadPool(String poolName, int nThreads, int queueCapacity) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        nThreads,
        nThreads,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(queueCapacity),
        createThreadFactory(poolName),
        new ThreadPoolExecutor.CallerRunsPolicy());
    log.info("[InternalExecutorFactory] 创建固定线程池 [{}] (threads={}, queue={})",
        poolName, nThreads, queueCapacity);
    return executor;
  }

  /**
   * 创建定时线程池（用于内部扫描/调度任务）。
   *
   * <p>适用于 Outbox 事件扫描、指标采集、健康检查等周期性任务场景。
   *
   * @param poolName 池名称
   * @param corePoolSize 核心线程数
   * @return 定时线程池实例
   */
  public static ScheduledExecutorService newScheduledThreadPool(String poolName, int corePoolSize) {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
        corePoolSize,
        createThreadFactory(poolName),
        new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setRemoveOnCancelPolicy(true);
    log.info("[InternalExecutorFactory] 创建定时线程池 [{}] (core={})", poolName, corePoolSize);
    return executor;
  }

  /**
   * 创建单线程定时线程池（用于轻量定时任务）。
   *
   * @param poolName 池名称
   * @return 单线程定时线程池实例
   */
  public static ScheduledExecutorService newSingleThreadScheduledPool(String poolName) {
    return newScheduledThreadPool(poolName, 1);
  }

  /**
   * 创建缓存线程池（适用于短生命周期、突发型内部任务）。
   *
   * <p>核心线程数为 0，最大线程数有上限，60s 空闲回收。使用 SynchronousQueue 避免任务堆积。
   *
   * @param poolName 池名称
   * @return 缓存线程池实例
   */
  public static ExecutorService newCachedThreadPool(String poolName) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        0,
        DEFAULT_MAX_POOL_SIZE,
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        createThreadFactory(poolName),
        new ThreadPoolExecutor.CallerRunsPolicy());
    log.info("[InternalExecutorFactory] 创建缓存线程池 [{}] (max={})", poolName, DEFAULT_MAX_POOL_SIZE);
    return executor;
  }

  /**
   * 创建 CPU 密集型线程池（核心数 + 1，有界队列）。
   *
   * @param poolName 池名称
   * @return CPU 密集型线程池实例
   */
  public static ExecutorService newCpuBoundThreadPool(String poolName) {
    int corePoolSize = Runtime.getRuntime().availableProcessors() + 1;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        corePoolSize,
        corePoolSize,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
        createThreadFactory(poolName),
        new ThreadPoolExecutor.CallerRunsPolicy());
    log.info("[InternalExecutorFactory] 创建 CPU 密集型线程池 [{}] (cores={})", poolName, corePoolSize);
    return executor;
  }

  /**
   * 创建自定义线程池。
   *
   * @param poolName 池名称
   * @param corePoolSize 核心线程数
   * @param maximumPoolSize 最大线程数
   * @param keepAliveTime 空闲线程存活时间
   * @param unit 时间单位
   * @param workQueue 工作队列
   * @return 自定义线程池实例
   */
  public static ThreadPoolExecutor newCustomThreadPool(
      String poolName,
      int corePoolSize,
      int maximumPoolSize,
      long keepAliveTime,
      TimeUnit unit,
      BlockingQueue<Runnable> workQueue) {
    return newCustomThreadPool(poolName, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, null);
  }

  /**
   * 创建自定义线程池（含拒绝策略）。
   *
   * @param poolName 池名称
   * @param corePoolSize 核心线程数
   * @param maximumPoolSize 最大线程数
   * @param keepAliveTime 空闲线程存活时间
   * @param unit 时间单位
   * @param workQueue 工作队列
   * @param handler 拒绝策略（null 使用 CallerRunsPolicy）
   * @return 自定义线程池实例
   */
  public static ThreadPoolExecutor newCustomThreadPool(
      String poolName,
      int corePoolSize,
      int maximumPoolSize,
      long keepAliveTime,
      TimeUnit unit,
      BlockingQueue<Runnable> workQueue,
      RejectedExecutionHandler handler) {
    if (handler == null) {
      handler = new ThreadPoolExecutor.CallerRunsPolicy();
    }
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue,
        createThreadFactory(poolName),
        handler);
    log.info("[InternalExecutorFactory] 创建自定义线程池 [{}] (core={}, max={})",
        poolName, corePoolSize, maximumPoolSize);
    return executor;
  }

  /**
   * 创建线程工厂。
   *
   * <p>线程名格式：{@code ydsz-internal-{poolName}-{threadNumber}}，便于日志定位和监控区分。
   *
   * @param poolName 池名称
   * @return 线程工厂实例
   */
  private static ThreadFactory createThreadFactory(String poolName) {
    String finalPrefix = THREAD_NAME_PREFIX + poolName + "-";
    AtomicInteger threadNumber = new AtomicInteger(1);
    return r -> {
      Thread thread = new Thread(r);
      thread.setName(finalPrefix + threadNumber.getAndIncrement());
      thread.setDaemon(true);
      thread.setPriority(Thread.NORM_PRIORITY);
      thread.setUncaughtExceptionHandler((t, e) ->
          log.error("[InternalExecutorFactory] 线程 [{}] 未捕获异常", t.getName(), e));
      return thread;
    };
  }
}
