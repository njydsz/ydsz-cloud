package com.njydsz.cronjob.server.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.thread.registry.ThreadPoolRegistry;
import com.njydsz.cronjob.server.config.CronjobProperties;

/**
 * 线程池注册表（P1-A2）。
 *
 * <p>集中管理 ydsz-cronjob 模块所有线程池的生命周期，消除各组件自行创建
 * 导致的"找不到引用"和"反射强耦合"问题。
 *
 * <p><b>26.09.13 重构：</b>自建的本地 {@code pools} 注册表移除，委托 ydzz-common-thread 的
 * {@link ThreadPoolRegistry} 统一维护注册/查询/快照能力。本类保留以下 cronjob 特有逻辑：
 *
 * <ul>
 *   <li>拒绝计数包装（{@link CountingRejectedExecutionHandler}）：每个线程池注册时自动包装拒绝策略，
 *       使 {@link #getMetrics()} 能输出 {@code rejectedExecutionCount}</li>
 *   <li>优雅关闭（{@link #shutdownAll()}）：仅关闭本模块注册的线程池（通过 {@code cronjobPoolNames}
 *       集合限定范围），避免误关 common-thread 管理的全局线程池</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class CronjobThreadPoolRegistry {

  private final CronjobProperties cronjobProperties;

  /** 本模块注册的线程池名称集合（用于 shutdownAll 限定关闭范围） */
  private final Set<String> cronjobPoolNames = ConcurrentHashMap.newKeySet();

  /** P2-E1: 线程池拒绝计数: poolName -> 拒绝次数（由 CountingRejectedExecutionHandler 维护） */
  private final ConcurrentHashMap<String, AtomicLong> rejectedCounts = new ConcurrentHashMap<>();

  // ==================== 标准注册名常量 ====================

  /** 全局任务执行线程池（DefaultTaskDispatcher 主执行池） */
  public static final String GLOBAL_EXECUTOR = "cronjob-global-exec";

  /** 任务扫描派发线程池（JobScanner 并行派发池） */
  public static final String SCANNER_DISPATCH = "cronjob-scanner-dispatch";

  /** 失败重试调度线程池（DefaultTaskDispatcher 重试调度器） */
  public static final String RETRY_SCHEDULER = "cronjob-retry-scheduler";

  /** 租户隔离线程池前缀（实际注册名: tenant-isolation-{tenantId/jobGroup}） */
  public static final String TENANT_ISOLATION_PREFIX = "tenant-isolation-";

  /**
   * 注册一个线程池。
   *
   * <p>幂等操作：同名线程池若已存在，{@link ThreadPoolRegistry} 将跳过注册并打印警告日志。
   *
   * <p>本方法在完成拒绝计数包装后，将线程池登记到 ydsz-common-thread 的 {@link ThreadPoolRegistry}，
   * 使本模块线程池可通过 common 统一的 {@code /actuator/threadpools} 端点观测。
   *
   * @param name 线程池名称（常量参见本类静态字段）
   * @param pool 线程池实例
   * @throws IllegalArgumentException name 为 null/blank 或 pool 为 null
   */
  public void register(String name, ThreadPoolExecutor pool) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("线程池名称不可为空");
    }
    if (pool == null) {
      throw new IllegalArgumentException("线程池实例不可为 null");
    }
    // P2-E1: 包装拒绝处理器为计数版本，使 rejectedExecutionCount 可观测
    wrapRejectionCounter(name, pool);
    // 委托 common-thread 统一注册中心（putIfAbsent 语义，同名跳过）
    ThreadPoolRegistry.register(name, pool);
    cronjobPoolNames.add(name);
    log.info("[ThreadPoolRegistry] 注册线程池: name={}", name);
  }

  /**
   * P2-E1: 将线程池的拒绝处理器包装为计数版本。
   *
   * <p>仅在首次注册时包装（幂等判断：已是计数版本则跳过）。 包装不改变原拒绝策略行为（委托链），仅增加计数。
   */
  private void wrapRejectionCounter(String name, ThreadPoolExecutor pool) {
    AtomicLong counter = rejectedCounts.computeIfAbsent(name, k -> new AtomicLong());
    if (!(pool.getRejectedExecutionHandler() instanceof CountingRejectedExecutionHandler)) {
      pool.setRejectedExecutionHandler(
          new CountingRejectedExecutionHandler(pool.getRejectedExecutionHandler(), counter));
    }
  }

  /**
   * 根据名称查找线程池。
   *
   * @param name 线程池名称
   * @return 找到的线程池；不存在时返回 null
   */
  public ThreadPoolExecutor get(String name) {
    return ThreadPoolRegistry.get(name);
  }

  /**
   * 判断指定名称的线程池是否已注册。
   *
   * @param name 线程池名称
   * @return true 表示已注册且可用
   */
  public boolean contains(String name) {
    return ThreadPoolRegistry.contains(name);
  }

  /**
   * 移除已注册的线程池（仅从本模块跟踪集合中移除，不影响 common-thread 全局注册表）。
   *
   * <p>注意：ThreadPoolRegistry 不支持注销，此处仅移除本模块的跟踪记录。
   * 调用方应自行处理线程池关闭。
   *
   * @param name 线程池名称
   * @return true 如果本模块曾跟踪该线程池
   */
  public boolean unregister(String name) {
    boolean removed = cronjobPoolNames.remove(name);
    if (removed) {
      log.info("[ThreadPoolRegistry] 移除线程池跟踪: name={}", name);
    }
    return removed;
  }

  /**
   * 返回本模块已注册线程池的只读快照。
   *
   * <p>仅包含通过本注册表 {@link #register} 注册的线程池，不含 common-thread 管理的全局线程池。
   *
   * @return 不可变的名称 -> 线程池 映射
   */
  public Map<String, ThreadPoolExecutor> getAll() {
    if (cronjobPoolNames.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, ThreadPoolExecutor> result = new LinkedHashMap<>(cronjobPoolNames.size());
    for (String name : cronjobPoolNames) {
      ThreadPoolExecutor pool = ThreadPoolRegistry.get(name);
      if (pool != null) {
        result.put(name, pool);
      }
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * 返回本模块已注册线程池的运行指标快照。
   *
   * @return 指标列表（每项包含 name/corePoolSize/maximumPoolSize/activeCount/
   *     queueSize/completedTaskCount/rejectedExecutionCount）
   */
  public List<ThreadPoolMetrics> getMetrics() {
    List<ThreadPoolMetrics> metrics = new ArrayList<>(cronjobPoolNames.size());
    for (String name : cronjobPoolNames) {
      ThreadPoolExecutor pool = ThreadPoolRegistry.get(name);
      if (pool == null) {
        continue;
      }
      metrics.add(
          new ThreadPoolMetrics(
              name,
              pool.getCorePoolSize(),
              pool.getMaximumPoolSize(),
              pool.getActiveCount(),
              pool.getQueue() != null ? pool.getQueue().size() : 0,
              pool.getCompletedTaskCount(),
              pool.getLargestPoolSize(),
              // P2-E1: 读取真实拒绝计数（CountingRejectedExecutionHandler 维护）
              rejectedCounts.getOrDefault(name, new AtomicLong(0)).get()));
    }
    return metrics;
  }

  /**
   * 优雅关闭所有本模块注册的线程池。
   *
   * <p>Spring 容器销毁时自动调用。每个线程池按配置排空超时时间
   * （{@code schedulerAwaitTerminationSeconds}）等待，超时后强制 shutdownNow。
   *
   * <p>仅关闭通过本注册表注册的线程池（由 {@code cronjobPoolNames} 集合限定），
   * 不会关闭 common-thread 管理的全局线程池。
   */
  @PreDestroy
  public void shutdownAll() {
    int count = cronjobPoolNames.size();
    if (count == 0) {
      log.debug("[ThreadPoolRegistry] 无线程池需要关闭");
      return;
    }
    int awaitSeconds = cronjobProperties.getSchedulerAwaitTerminationSeconds();
    log.info("[ThreadPoolRegistry] 关闭所有线程池: count={} awaitSeconds={}", count, awaitSeconds);
    for (String name : cronjobPoolNames) {
      ThreadPoolExecutor pool = ThreadPoolRegistry.get(name);
      if (pool != null) {
        shutdownPool(name, pool, awaitSeconds);
      }
    }
    cronjobPoolNames.clear();
    log.info("[ThreadPoolRegistry] 所有线程池已关闭");
  }

  /**
   * 关闭单个线程池并等待排空。
   *
   * @param name 线程池名称（用于日志）
   * @param pool 线程池实例
   * @param awaitSeconds 最大等待秒数
   */
  private void shutdownPool(String name, ThreadPoolExecutor pool, int awaitSeconds) {
    try {
      pool.shutdown();
      if (!pool.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
        log.warn("[ThreadPoolRegistry] 线程池关闭超时, 强制终止: name={}", name);
        pool.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      pool.shutdownNow();
    } catch (Exception e) {
      log.warn("[ThreadPoolRegistry] 关闭线程池异常: name={} reason={}", name, e.getMessage());
    }
  }

  /**
   * 线程池运行时指标快照（不可变）。
   *
   * @param name 线程池名称
   * @param corePoolSize 核心线程数
   * @param maximumPoolSize 最大线程数
   * @param activeCount 活跃线程数
   * @param queueSize 队列中等待任务数
   * @param completedTaskCount 已完成任务总数
   * @param largestPoolSize 历史最大线程池大小
   * @param rejectedExecutionCount 被拒绝任务数
   */
  public record ThreadPoolMetrics(
      String name,
      int corePoolSize,
      int maximumPoolSize,
      int activeCount,
      int queueSize,
      long completedTaskCount,
      int largestPoolSize,
      long rejectedExecutionCount) {}
}
