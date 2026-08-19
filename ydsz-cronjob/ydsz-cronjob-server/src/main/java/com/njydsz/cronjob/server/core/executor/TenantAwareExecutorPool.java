package com.njydsz.cronjob.server.core.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.ExecutorConfig;

/**
 * P2-5: 租户感知的线程池（分桶策略）。
 *
 * <p>使用固定数量的分桶池（默认 8 个），通过哈希将租户/分组映射到对应分桶：
 *
 * <ul>
 *   <li>策略为 {@code none}：返回 null，由调用方使用全局池
 *   <li>策略为 {@code tenant}：按 {@code tenantId} 哈希选择分桶
 *   <li>策略为 {@code job_group}：按 {@code jobGroup} 哈希选择分桶
 * </ul>
 *
 * <h3>分桶策略优势</h3>
 *
 * <ul>
 *   <li>池数量固定，避免无限创建导致资源耗尽
 *   <li>同一分桶内的租户共享线程池，提高资源利用率
 *   <li>不同分桶间天然隔离，单个分桶压力大不影响其他分桶
 * </ul>
 *
 * <h3>线程池参数</h3>
 *
 * <ul>
 *   <li>分桶数量 = {@code executor.isolation-buckets}（默认 8）
 *   <li>核心线程数 = {@code executor.tenant-pool-size}（默认 10）
 *   <li>队列容量 = {@code executor.tenant-pool-queue-capacity}（默认 200）
 *   <li>拒绝策略 = {@code CallerRunsPolicy}（自然背压，调用线程同步执行）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAwareExecutorPool {

  private final CronjobProperties cronjobProperties;

  /** 固定分桶池数组 */
  private ExecutorService[] buckets;

  /** 分桶数量（初始化后不可变） */
  private int bucketCount;

  /**
   * 根据隔离策略返回对应的分桶线程池。
   *
   * <p>策略为 {@code none} 时返回 null，由调用方使用全局池。
   *
   * @param tenantId 租户 ID（可能为 null）
   * @param jobGroup 任务分组（可能为 null）
   * @return 分桶线程池；策略=none 或 key 为空时返回 null
   */
  public ExecutorService getExecutor(String tenantId, String jobGroup) {
    String strategy = cronjobProperties.getExecutor().getIsolationStrategy();
    if (strategy == null || "none".equalsIgnoreCase(strategy)) {
      return null;
    }
    String key = resolveKey(strategy, tenantId, jobGroup);
    if (key == null || key.isBlank()) {
      return null;
    }
    if (buckets == null) {
      initBuckets();
    }
    int index = Math.floorMod(key.hashCode(), bucketCount);
    return buckets[index];
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
   * 初始化固定分桶池数组。
   *
   * <p>使用 floorMod 保证非负分桶索引。
   */
  private synchronized void initBuckets() {
    if (buckets != null) {
      return;
    }
    ExecutorConfig execConfig = cronjobProperties.getExecutor();
    this.bucketCount = Math.max(1, execConfig.getIsolationBuckets());
    this.buckets = new ExecutorService[bucketCount];
    for (int i = 0; i < bucketCount; i++) {
      buckets[i] = createBucketPool(i);
    }
    log.info(
        "[TenantAwarePool] 分桶池初始化完成: bucketCount={} coreSize={} queueCapacity={}",
        bucketCount,
        execConfig.getTenantPoolSize(),
        execConfig.getTenantPoolQueueCapacity());
  }

  /**
   * 创建一个分桶线程池。
   *
   * <p><b>注意：</b>此处分桶池按租户/分组哈希隔离，池数量固定（由 {@code executor.isolation-buckets} 控制），
   * 不属于无限创建场景。如需统一管理可配置 {@code ydsz.thread.pools.cronjobTenant} 并通过
   * 外部注入替换此方法逻辑。
   *
   * @param bucketIndex 分桶索引（用于线程命名）
   * @return 新建的线程池
   */
  private ExecutorService createBucketPool(int bucketIndex) {
    ExecutorConfig execConfig = cronjobProperties.getExecutor();
    int corePoolSize = Math.max(1, execConfig.getTenantPoolSize());
    int maxPoolSize = Math.max(corePoolSize, execConfig.getTenantPoolSize());
    int queueCapacity = Math.max(0, execConfig.getTenantPoolQueueCapacity());
    LinkedBlockingQueue<Runnable> workQueue =
        queueCapacity == 0 ? new LinkedBlockingQueue<>() : new LinkedBlockingQueue<>(queueCapacity);
    // CHECKSTYLE.OFF: RegexpSinglelineJava - 分桶隔离池，桶数量固定且有限
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L,
            TimeUnit.SECONDS,
            workQueue,
            r -> {
              Thread t = new Thread(r, "job-tenant-bucket-" + bucketIndex + "-" + System.nanoTime());
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());
    // CHECKSTYLE.ON: RegexpSinglelineJava
    log.debug("[TenantAwarePool] 创建分桶池: index={} core={} max={} queue={}",
        bucketCount, corePoolSize, maxPoolSize, queueCapacity);
    return pool;
  }

  /**
   * P0-FIX: 清空所有分桶线程池（隔离策略/池参数热更新时调用）。
   *
   * <p>关闭现有分桶池并置空数组，下次 {@link #getExecutor} 时按最新配置懒重建
   * （配合 ThreadPoolHotUpdateListener 的 isolationStrategy/tenantPoolSize 变更）。
   *
   * <p>注：原代码在 ThreadPoolHotUpdateListener 中调用 {@code evictAllPools()} 但本类未实现该方法
   * （编译失败），此处补齐。关闭逻辑复用 {@link #shutdownAll()}。
   */
  public synchronized void evictAllPools() {
    ExecutorService[] oldBuckets = buckets;
    int oldCount = bucketCount;
    buckets = null;
    bucketCount = 0;
    if (oldBuckets != null) {
      for (int i = 0; i < oldCount; i++) {
        ExecutorService pool = oldBuckets[i];
        if (pool != null) {
          pool.shutdown();
        }
      }
      log.info("[TenantAwarePool] 已清空分桶池（下次按新配置懒重建）: oldCount={}", oldCount);
    }
  }

  /**
   * 优雅关闭所有分桶线程池。
   *
   * <p>Spring 容器销毁时调用。每个线程池最多等待 5s 排空在执行任务，超时后强制 shutdownNow。
   */
  @PreDestroy
  public void shutdownAll() {
    if (buckets == null) {
      return;
    }
    log.info("[TenantAwarePool] 关闭所有分桶池: count={}", bucketCount);
    for (int i = 0; i < bucketCount; i++) {
      ExecutorService pool = buckets[i];
      if (pool != null) {
        try {
          pool.shutdown();
          if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          pool.shutdownNow();
        } catch (Exception e) {
          log.warn("[TenantAwarePool] 关闭分桶池异常: index={} reason={}", i, e.getMessage());
        }
      }
    }
    buckets = null;
    log.info("[TenantAwarePool] 所有分桶池已关闭");
  }
}
