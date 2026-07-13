package com.njydsz.pmis.common.cache.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;

/**
 * 缓存预热器 - 在应用启动时异步预加载热点数据到缓存
 *
 * <p>核心功能：
 *
 * <ul>
 *   <li>批量预热：支持从数据源批量加载热点数据到缓存
 *   <li>进度监控：实时跟踪预热进度、成功/失败数量
 *   <li>失败重试：预热失败可自动重试，支持配置重试次数和间隔
 *   <li>异步执行：预热过程不阻塞应用启动
 *   <li>完成回调：预热完成/失败时触发回调通知
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * Cache<String, User> cache = YdszCache.newBuilder()
 *     .maximumSize(10000)
 *     .build();
 *
 * // 创建预热器
 * CacheWarmer<String, User> warmer = new CacheWarmer<>(cache);
 *
 * // 定义数据加载器
 * CacheLoader<String, User> loader = CacheLoader.from(key -> userDao.findById(key));
 *
 * // 配置预热参数
 * warmer.warmUpAsync(keys, loader)
 *       .maxRetries(3)
 *       .retryDelay(1000, TimeUnit.MILLISECONDS)
 *       .onComplete(stats -> log.info("预热完成: {}", stats))
 *       .execute();
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class CacheWarmer<K, V> {

  private static final Logger log = LoggerFactory.getLogger(CacheWarmer.class);

  private static final int DEFAULT_MAX_RETRIES = 0;
  private static final long DEFAULT_RETRY_DELAY_MS = 1000;
  private static final int DEFAULT_BATCH_SIZE = 100;
  private static final int DEFAULT_CONCURRENCY = 4;

  private final Cache<K, V> cache;
  private final ExecutorService executor;
  private final boolean ownsExecutor;

  private int maxRetries = DEFAULT_MAX_RETRIES;
  private long retryDelayMs = DEFAULT_RETRY_DELAY_MS;
  private int batchSize = DEFAULT_BATCH_SIZE;
  private Consumer<WarmupStats> onCompleteCallback;

  /**
   * 创建缓存预热器（使用内置线程池）
   *
   * @param cache 目标缓存
   */
  public CacheWarmer(Cache<K, V> cache) {
    this.cache = cache;
    this.executor =
        Executors.newFixedThreadPool(
            DEFAULT_CONCURRENCY,
            r -> {
              Thread t = new Thread(r, "CacheWarmer");
              t.setDaemon(true);
              return t;
            });
    this.ownsExecutor = true;
  }

  /**
   * 创建缓存预热器（使用自定义线程池）
   *
   * @param cache 目标缓存
   * @param executor 自定义线程池
   */
  public CacheWarmer(Cache<K, V> cache, ExecutorService executor) {
    this.cache = cache;
    this.executor = executor;
    this.ownsExecutor = false;
  }

  /**
   * 设置最大重试次数
   *
   * @param maxRetries 最大重试次数，默认 0（不重试）
   * @return this
   */
  public CacheWarmer<K, V> maxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
    return this;
  }

  /**
   * 设置重试延迟时间
   *
   * @param delay 延迟时间
   * @param unit 时间单位
   * @return this
   */
  public CacheWarmer<K, V> retryDelay(long delay, TimeUnit unit) {
    this.retryDelayMs = unit.toMillis(delay);
    return this;
  }

  /**
   * 设置批量预热大小
   *
   * @param batchSize 每批处理的 key 数量，默认 100
   * @return this
   */
  public CacheWarmer<K, V> batchSize(int batchSize) {
    this.batchSize = batchSize;
    return this;
  }

  /**
   * 设置预热完成回调
   *
   * @param callback 回调函数，接收预热统计信息
   * @return this
   */
  public CacheWarmer<K, V> onComplete(Consumer<WarmupStats> callback) {
    this.onCompleteCallback = callback;
    return this;
  }

  /**
   * 异步预热缓存
   *
   * @param keys 需要预热的 key 集合
   * @param loader 数据加载器
   * @return WarmupTask 实例，可用于等待完成或取消
   */
  public WarmupTask warmUpAsync(Collection<K> keys, CacheLoader<K, V> loader) {
    return new WarmupTask(keys, loader);
  }

  /**
   * 同步预热缓存（阻塞直到完成）
   *
   * @param keys 需要预热的 key 集合
   * @param loader 数据加载器
   * @return 预热统计信息
   */
  public WarmupStats warmUpSync(Collection<K> keys, CacheLoader<K, V> loader) {
    WarmupTask task = warmUpAsync(keys, loader);
    return task.waitForCompletion();
  }

  /** 关闭预热器，释放线程池资源 */
  public void shutdown() {
    if (ownsExecutor && !executor.isShutdown()) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
      log.info("CacheWarmer 已关闭");
    }
  }

  /** 预热任务，支持异步执行和进度监控 */
  public class WarmupTask {

    private final Collection<K> keys;
    private final CacheLoader<K, V> loader;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger totalKeys;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicLong startTime = new AtomicLong(0);
    private final AtomicLong endTime = new AtomicLong(0);
    private final List<K> failedKeys = new CopyOnWriteArrayList<>();

    WarmupTask(Collection<K> keys, CacheLoader<K, V> loader) {
      this.keys = keys;
      this.loader = loader;
      this.totalKeys = new AtomicInteger(keys.size());
    }

    /** 执行预热任务 */
    public void execute() {
      executor.submit(this::doWarmup);
    }

    /**
     * 等待预热完成（阻塞）
     *
     * @return 预热统计信息
     */
    public WarmupStats waitForCompletion() {
      try {
        doWarmup();
      } catch (Exception e) {
        log.error("缓存预热异常", e);
      }
      return getStats();
    }

    /** 取消预热任务 */
    public void cancel() {
      cancelled.set(true);
      log.info("缓存预热任务已取消");
    }

    /** 获取当前预热进度（0.0 ~ 1.0） */
    public double getProgress() {
      int total = totalKeys.get();
      if (total == 0) {
        return 1.0;
      }
      return (double) (successCount.get() + failCount.get()) / total;
    }

    /** 获取预热统计信息 */
    public WarmupStats getStats() {
      return new WarmupStats(
          totalKeys.get(),
          successCount.get(),
          failCount.get(),
          retryCount.get(),
          startTime.get(),
          endTime.get(),
          cancelled.get(),
          new ArrayList<>(failedKeys));
    }

    private void doWarmup() {
      if (keys == null || keys.isEmpty()) {
        log.info("缓存预热：无数据需要预热");
        endTime.set(System.currentTimeMillis());
        invokeCallback();
        return;
      }

      startTime.set(System.currentTimeMillis());
      log.info(
          "缓存预热开始，totalKeys={}, maxRetries={}, batchSize={}", keys.size(), maxRetries, batchSize);

      List<K> keyList = new ArrayList<>(keys);
      int batchCount = (keyList.size() + batchSize - 1) / batchSize;

      for (int batchIdx = 0; batchIdx < batchCount; batchIdx++) {
        if (cancelled.get()) {
          break;
        }

        int fromIndex = batchIdx * batchSize;
        int toIndex = Math.min(fromIndex + batchSize, keyList.size());
        List<K> batch = keyList.subList(fromIndex, toIndex);

        List<Future<?>> futures = new ArrayList<>();
        for (K key : batch) {
          if (cancelled.get()) {
            break;
          }
          futures.add(executor.submit(() -> warmUpSingle(key)));
        }

        for (Future<?> future : futures) {
          try {
            future.get();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          } catch (ExecutionException e) {
            log.warn("缓存预热任务执行异常，key={}", keyList.get(fromIndex), e.getCause());
          }
        }
      }

      endTime.set(System.currentTimeMillis());
      long duration = endTime.get() - startTime.get();
      log.info(
          "缓存预热完成，total={}, success={}, failed={}, retries={}, duration={}ms",
          totalKeys.get(),
          successCount.get(),
          failCount.get(),
          retryCount.get(),
          duration);

      invokeCallback();
    }

    private void warmUpSingle(K key) {
      int attempts = 0;
      int maxAttempts = maxRetries + 1;

      while (attempts < maxAttempts) {
        if (cancelled.get()) {
          return;
        }

        try {
          V value = loader.load(key);
          if (value != null) {
            cache.put(key, value);
            successCount.incrementAndGet();
            return;
          } else {
            log.debug("缓存预热：loader 返回 null，跳过 key={}", key);
            failCount.incrementAndGet();
            failedKeys.add(key);
            return;
          }
        } catch (Exception e) {
          attempts++;
          retryCount.incrementAndGet();
          if (attempts < maxAttempts) {
            log.warn("缓存预热失败，重试中，key={}, attempt={}/{}", key, attempts, maxAttempts, e);
            try {
              Thread.sleep(retryDelayMs);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              return;
            }
          } else {
            log.error("缓存预热失败，已达最大重试次数，key={}", key, e);
            failCount.incrementAndGet();
            failedKeys.add(key);
          }
        }
      }
    }

    private void invokeCallback() {
      if (onCompleteCallback != null) {
        try {
          onCompleteCallback.accept(getStats());
        } catch (Exception e) {
          log.warn("预热完成回调执行异常", e);
        }
      }
    }
  }

  /** 预热统计信息 */
  public static class WarmupStats {

    private final int totalKeys;
    private final int successCount;
    private final int failCount;
    private final int retryCount;
    private final long startTime;
    private final long endTime;
    private final boolean cancelled;
    private final List<?> failedKeys;

    WarmupStats(
        int totalKeys,
        int successCount,
        int failCount,
        int retryCount,
        long startTime,
        long endTime,
        boolean cancelled,
        List<?> failedKeys) {
      this.totalKeys = totalKeys;
      this.successCount = successCount;
      this.failCount = failCount;
      this.retryCount = retryCount;
      this.startTime = startTime;
      this.endTime = endTime;
      this.cancelled = cancelled;
      this.failedKeys = failedKeys;
    }

    public int getTotalKeys() {
      return totalKeys;
    }

    public int getSuccessCount() {
      return successCount;
    }

    public int getFailCount() {
      return failCount;
    }

    public int getRetryCount() {
      return retryCount;
    }

    public long getStartTime() {
      return startTime;
    }

    public long getEndTime() {
      return endTime;
    }

    public long getDuration() {
      return endTime > 0 ? endTime - startTime : 0;
    }

    public boolean isCancelled() {
      return cancelled;
    }

    public List<?> getFailedKeys() {
      return failedKeys;
    }

    public double getSuccessRate() {
      return totalKeys > 0 ? (double) successCount / totalKeys : 0.0;
    }

    @Override
    public String toString() {
      return String.format(
          "WarmupStats{total=%d, success=%d, failed=%d, retries=%d, duration=%dms, cancelled=%s, successRate=%.2f%%}",
          totalKeys,
          successCount,
          failCount,
          retryCount,
          getDuration(),
          cancelled,
          getSuccessRate() * 100);
    }
  }
}
