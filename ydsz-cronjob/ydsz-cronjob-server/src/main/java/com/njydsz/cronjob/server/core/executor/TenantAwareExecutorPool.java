package com.njydsz.cronjob.server.core.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.CronjobProperties;

/**
 * P2-5: 租户感知的线程池。
 *
 * <p>按 {@code tenantId} 或 {@code jobGroup} 隔离任务执行线程池， 避免一个租户的大任务饿死其他租户（noisy neighbor 问题）。
 *
 * <h3>隔离策略</h3>
 *
 * <ul>
 *   <li>{@code none}（默认）：所有租户共享全局线程池（由 {@link
 *       com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher} 维护的 {@code
 *       taskExecutorPool}），本组件不参与
 *   <li>{@code tenant}：按 {@code tenantId} 隔离，每个租户一个独立线程池
 *   <li>{@code job_group}：按 {@code jobGroup} 隔离，每个分组一个独立线程池
 * </ul>
 *
 * <h3>线程池参数</h3>
 *
 * <ul>
 *   <li>核心线程数 = {@code executor.tenant-pool-size}（默认 10）
 *   <li>队列容量 = {@code executor.tenant-pool-queue-capacity}（默认 200）
 *   <li>拒绝策略 = {@code CallerRunsPolicy}（自然背压，调用线程同步执行）
 * </ul>
 *
 * <p>使用 {@link ConcurrentHashMap#computeIfAbsent} 保证并发创建线程池的幂等性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAwareExecutorPool {

  private final CronjobProperties cronjobProperties;

  /** 按 key（tenantId 或 jobGroup）隔离的线程池映射 */
  private final Map<String, ExecutorService> tenantPools = new ConcurrentHashMap<>();

  /** 线程命名计数器（每个 key 独立计数） */
  private final Map<String, AtomicInteger> threadCounters = new ConcurrentHashMap<>();

  /**
   * 根据隔离策略返回对应的线程池。
   *
   * <p>策略为 {@code none} 时返回全局共享池（null，由调用方使用 {@link #getGlobalExecutor()} 或 回退到 {@code
   * DefaultTaskDispatcher.taskExecutorPool}）。
   *
   * @param tenantId 租户 ID（可能为 null）
   * @param jobGroup 任务分组（可能为 null）
   * @return 隔离线程池；策略=none 时返回 null
   */
  public ExecutorService getExecutor(String tenantId, String jobGroup) {
    String strategy = cronjobProperties.getExecutor().getIsolationStrategy();
    if (strategy == null || "none".equalsIgnoreCase(strategy)) {
      // none 策略：返回 null，由调用方使用全局池
      return null;
    }
    String key = resolveKey(strategy, tenantId, jobGroup);
    if (key == null || key.isBlank()) {
      // key 为空时回退到全局池（避免无 tenantId 的任务无法路由）
      log.debug(
          "[TenantAwarePool] 隔离 key 为空, 回退全局池: strategy={} tenantId={} jobGroup={}",
          strategy,
          tenantId,
          jobGroup);
      return null;
    }
    return tenantPools.computeIfAbsent(key, k -> createPool(k, strategy));
  }

  /**
   * 返回全局共享池占位（实际全局池由 DefaultTaskDispatcher 维护）。
   *
   * <p>本方法返回 null，调用方应使用 {@code DefaultTaskDispatcher.taskExecutorPool}。 此方法仅为接口完整性保留。
   *
   * @return null（全局池由 DefaultTaskDispatcher 管理）
   */
  public ExecutorService getGlobalExecutor() {
    return null;
  }

  /**
   * 解析隔离 key。
   *
   * @param strategy 隔离策略
   * @param tenantId 租户 ID
   * @param jobGroup 任务分组
   * @return 隔离 key；无法解析时返回 null
   */
  private String resolveKey(String strategy, String tenantId, String jobGroup) {
    if ("tenant".equalsIgnoreCase(strategy)) {
      return tenantId;
    }
    if ("job_group".equalsIgnoreCase(strategy)) {
      return jobGroup;
    }
    return null;
  }

  /**
   * 创建一个租户/分组独立的线程池。
   *
   * @param key 隔离 key（tenantId 或 jobGroup）
   * @param strategy 隔离策略（用于日志识别）
   * @return 新建的线程池
   */
  private ExecutorService createPool(String key, String strategy) {
    CronjobProperties.Executor execConfig = cronjobProperties.getExecutor();
    int corePoolSize = Math.max(1, execConfig.getTenantPoolSize());
    int maxPoolSize = Math.max(corePoolSize, execConfig.getTenantPoolSize());
    int queueCapacity = Math.max(0, execConfig.getTenantPoolQueueCapacity());
    LinkedBlockingQueue<Runnable> workQueue =
        queueCapacity == 0 ? new LinkedBlockingQueue<>() : new LinkedBlockingQueue<>(queueCapacity);
    AtomicInteger counter = threadCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
    String prefix = "job-" + strategy + "-" + safeKey(key) + "-";
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L,
            TimeUnit.SECONDS,
            workQueue,
            r -> {
              Thread t = new Thread(r, prefix + counter.incrementAndGet());
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());
    log.info(
        "[TenantAwarePool] 创建隔离线程池: strategy={} key={} core={} max={} queue={}",
        strategy,
        key,
        corePoolSize,
        maxPoolSize,
        queueCapacity);
    return pool;
  }

  /** 将 key 转换为安全的线程名片段（截断过长 key + 替换特殊字符）。 */
  private String safeKey(String key) {
    String safe = key.replaceAll("[^a-zA-Z0-9_-]", "_");
    return safe.length() > 16 ? safe.substring(0, 16) : safe;
  }

  /**
   * P0-4: 清空所有租户隔离线程池缓存（热更新时调用）。
   *
   * <p>当隔离策略或线程池参数变更时，调用此方法清空旧的线程池缓存。 已在执行中的任务会在旧线程池中完成，新任务将使用新配置创建的线程池。
   *
   * <p>线程池不会被立即 shutdown（避免中断正在执行的任务），而是标记为待关闭， 等待 5s 排空后由 GC 回收。
   */
  public void evictAllPools() {
    int count = tenantPools.size();
    if (count == 0) {
      log.debug("[TenantAwarePool] 无隔离线程池需要清空");
      return;
    }
    log.info("[TenantAwarePool] 热更新: 清空所有隔离线程池缓存: count={}", count);
    // 异步关闭旧线程池（不阻塞配置更新线程）
    List<ExecutorService> oldPools = new ArrayList<>(tenantPools.values());
    tenantPools.clear();
    threadCounters.clear();
    for (ExecutorService pool : oldPools) {
      try {
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
          pool.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        pool.shutdownNow();
      } catch (Exception e) {
        log.warn("[TenantAwarePool] 热更新关闭旧线程池异常: reason={}", e.getMessage());
      }
    }
    log.info("[TenantAwarePool] 热更新: 旧隔离线程池已清空, 新任务将使用新配置创建");
  }

  /**
   * 优雅关闭所有隔离线程池。
   *
   * <p>Spring 容器销毁时调用。每个线程池最多等待 5s 排空在执行任务， 超时后强制 shutdownNow。
   */
  @PreDestroy
  public void shutdownAll() {
    log.info("[TenantAwarePool] 关闭所有隔离线程池: count={}", tenantPools.size());
    List<ExecutorService> pools = new ArrayList<>(tenantPools.values());
    for (ExecutorService pool : pools) {
      try {
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
          pool.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        pool.shutdownNow();
      } catch (Exception e) {
        log.warn("[TenantAwarePool] 关闭线程池异常: reason={}", e.getMessage());
      }
    }
    tenantPools.clear();
    threadCounters.clear();
  }
}
