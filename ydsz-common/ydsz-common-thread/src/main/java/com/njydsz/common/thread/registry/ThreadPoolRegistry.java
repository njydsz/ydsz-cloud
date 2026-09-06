package com.njydsz.common.thread.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 线程池统一注册中心（全局单例）。
 *
 * <p>使用 {@link ConcurrentHashMap} 维护 {@code threadPoolName -> ThreadPoolExecutor} 的映射，
 * 供全局注册、查询、指标快照采集使用。
 *
 * <p>注册时机：
 *
 * <ul>
 *   <li>{@code ExecutorUtils} 工厂方法创建 {@link ThreadPoolExecutor} 后自动注册
 *   <li>{@code ThreadPoolExecutorFactory} 创建 {@code ThreadPoolTaskExecutor} 后自动注册其底层执行器
 *   <li>Spring 容器启动后扫描所有 {@link Executor} / {@link ThreadPoolExecutor} / {@link
 *       org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor} Bean 并补注册
 * </ul>
 *
 * <p>线程安全：注册中心内部使用 {@link ConcurrentHashMap}，天然支持并发读写。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public final class ThreadPoolRegistry {

  /** 线程池注册映射：poolName → ThreadPoolExecutor */
  private static final ConcurrentHashMap<String, ThreadPoolExecutor> REGISTRY =
      new ConcurrentHashMap<>();

  private ThreadPoolRegistry() {
    throw new UnsupportedOperationException("ThreadPoolRegistry is a utility class");
  }

  // ==================== 核心 API ====================

  /**
   * 注册线程池。
   *
   * <p>若同名线程池已存在，将跳过注册并打印警告日志（避免意外覆盖）。
   *
   * @param name 线程池唯一名称
   * @param executor 线程池执行器实例
   * @throws IllegalArgumentException name 或 executor 为 null 时抛出
   */
  public static void register(String name, ThreadPoolExecutor executor) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("ThreadPoolRegistry: name must not be null or empty");
    }
    if (executor == null) {
      throw new IllegalArgumentException("ThreadPoolRegistry: executor must not be null");
    }
    ThreadPoolExecutor existing = REGISTRY.putIfAbsent(name, executor);
    if (existing != null) {
      log.warn(
          "[ThreadPoolRegistry] 线程池 [{}] 已存在，跳过重复注册（避免覆盖）",
          name);
    } else {
      log.info(
          "[ThreadPoolRegistry] 注册线程池 [{}] (core={}, max={}, queue={})",
          name,
          executor.getCorePoolSize(),
          executor.getMaximumPoolSize(),
          executor.getQueue().remainingCapacity());
    }
  }

  /**
   * 根据名称获取线程池。
   *
   * @param name 线程池名称
   * @return 线程池实例；不存在返回 {@code null}
   */
  public static ThreadPoolExecutor get(String name) {
    return REGISTRY.get(name);
  }

  /**
   * 获取所有已注册线程池的不可变视图。
   *
   * @return poolName → ThreadPoolExecutor 的不可变映射
   */
  public static Map<String, ThreadPoolExecutor> getAll() {
    return Collections.unmodifiableMap(REGISTRY);
  }

  /**
   * 检查指定名称的线程池是否已注册。
   *
   * @param name 线程池名称
   * @return true 如果已注册
   */
  public static boolean contains(String name) {
    return REGISTRY.containsKey(name);
  }

  /**
   * 返回已注册线程池数量。
   *
   * @return 注册中心中线程池数量
   */
  public static int size() {
    return REGISTRY.size();
  }

  // ==================== 指标快照 ====================

  /**
   * 采集所有已注册线程池的实时指标快照。
   *
   * <p>返回的快照列表按线程池名称排序，适用于监控端点序列化展示。
   *
   * @return 所有线程池的指标快照列表
   */
  public static List<ThreadPoolMetricsSnapshot> snapshotMetrics() {
    List<ThreadPoolMetricsSnapshot> snapshots = new ArrayList<>(REGISTRY.size());
    for (Map.Entry<String, ThreadPoolExecutor> entry : REGISTRY.entrySet()) {
      snapshots.add(snapshot(entry.getKey(), entry.getValue()));
    }
    snapshots.sort((a, b) -> a.poolName.compareTo(b.poolName));
    return snapshots;
  }

  /**
   * 采集指定线程池的实时指标快照。
   *
   * @param name 线程池名称
   * @return 指标快照；线程池不存在返回 {@code null}
   */
  public static ThreadPoolMetricsSnapshot snapshotMetrics(String name) {
    ThreadPoolExecutor executor = REGISTRY.get(name);
    if (executor == null) {
      return null;
    }
    return snapshot(name, executor);
  }

  /**
   * 采集指定线程池的指标快照。
   *
   * @param name 线程池名称
   * @param executor 线程池执行器
   * @return 指标快照
   */
  private static ThreadPoolMetricsSnapshot snapshot(String name, ThreadPoolExecutor executor) {
    int queueSize = executor.getQueue() != null ? executor.getQueue().size() : 0;
    int queueRemaining = executor.getQueue() != null ? executor.getQueue().remainingCapacity() : 0;
    int queueCapacity = queueSize + queueRemaining;
    return new ThreadPoolMetricsSnapshot(
        name,
        executor.getCorePoolSize(),
        executor.getMaximumPoolSize(),
        executor.getActiveCount(),
        executor.getPoolSize(),
        queueSize,
        queueCapacity,
        executor.getCompletedTaskCount(),
        executor.getLargestPoolSize(),
        executor.getTaskCount());
  }

  // ==================== 内部数据类 ====================

  /**
   * 线程池实时指标快照。
   *
   * <p>不可变数据类，用于端点序列化和监控数据采集。
   *
   * @since 26.09.01
   */
  public static final class ThreadPoolMetricsSnapshot {
    /** 线程池名称 */
    private final String poolName;
    /** 核心线程数 */
    private final int corePoolSize;
    /** 最大线程数 */
    private final int maximumPoolSize;
    /** 当前活跃线程数 */
    private final int activeCount;
    /** 当前线程池大小（已创建的线程数） */
    private final int poolSize;
    /** 工作队列当前大小 */
    private final int queueSize;
    /** 工作队列总容量（含已占用 + 剩余） */
    private final int queueCapacity;
    /** 累计完成任务数 */
    private final long completedTaskCount;
    /** 历史最大线程池大小 */
    private final int largestPoolSize;
    /** 累计任务总数（含队列中待执行） */
    private final long taskCount;

    public ThreadPoolMetricsSnapshot(
        String poolName,
        int corePoolSize,
        int maximumPoolSize,
        int activeCount,
        int poolSize,
        int queueSize,
        int queueCapacity,
        long completedTaskCount,
        int largestPoolSize,
        long taskCount) {
      this.poolName = poolName;
      this.corePoolSize = corePoolSize;
      this.maximumPoolSize = maximumPoolSize;
      this.activeCount = activeCount;
      this.poolSize = poolSize;
      this.queueSize = queueSize;
      this.queueCapacity = queueCapacity;
      this.completedTaskCount = completedTaskCount;
      this.largestPoolSize = largestPoolSize;
      this.taskCount = taskCount;
    }

    public String getPoolName() {
      return poolName;
    }

    public int getCorePoolSize() {
      return corePoolSize;
    }

    public int getMaximumPoolSize() {
      return maximumPoolSize;
    }

    public int getActiveCount() {
      return activeCount;
    }

    public int getPoolSize() {
      return poolSize;
    }

    public int getQueueSize() {
      return queueSize;
    }

    public int getQueueCapacity() {
      return queueCapacity;
    }

    public long getCompletedTaskCount() {
      return completedTaskCount;
    }

    public int getLargestPoolSize() {
      return largestPoolSize;
    }

    public long getTaskCount() {
      return taskCount;
    }
  }
}
