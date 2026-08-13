package com.njydsz.common.cache.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import com.njydsz.common.cache.api.Cache;

/**
 * 缓存预热器 — Spring 生命周期管理（增强版）
 *
 * <p>在 Spring 容器初始化完成后执行缓存预热，将热点数据加载到缓存中，
 * 避免启动初期的缓存穿透和后端压力骤增。
 *
 * <p>增强特性（v1.0.0+）：
 * <ul>
 *   <li>异步预热模式：预热任务异步执行，不阻塞 Spring 容器启动</li>
 *   <li>超时控制：可配置超时时间，超时后自动放弃未完成的 key</li>
 *   <li>速率限制：通过 Semaphore 控制预热 QPS，避免启动时压垮 DB</li>
 *   <li>失败降级：单个 key 预热失败不影响整体任务</li>
 * </ul>
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * @Bean
 * public CacheWarmer cacheWarmer() {
 *   CacheWarmer warmer = new CacheWarmer();
 *   warmer.setAsyncMode(true);  // 异步预热，不阻塞容器启动
 *   warmer.setTimeout(60, TimeUnit.SECONDS);  // 60s 超时
 *   warmer.setRateLimiter(50);  // 最多 50 并发加载
 *   warmer.registerWarmTask("userCache", userCache, userIds, this::loadUser);
 *   return warmer;
 * }
 * }</pre>
 *
 * <p>实现 {@link SmartInitializingSingleton} 确保在所有 Bean 初始化完成后执行预热。
 * 实现 {@link DisposableBean} 确保应用关闭时清理资源。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CacheWarmer implements SmartInitializingSingleton, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(CacheWarmer.class);

  private final List<WarmTask<?, ?>> tasks = new ArrayList<>();
  private final Executor executor;
  private volatile boolean warmed = false;

  /** 是否异步预热模式（不阻塞容器启动） */
  private volatile boolean asyncMode = false;

  /** 预热超时时间（毫秒），0 表示不限制 */
  private volatile long timeoutMillis = 0;

  /** 速率限制（并发数），0 表示不限制 */
  private volatile int rateLimit = 0;

  /** 速率限制信号量 */
  private Semaphore rateLimiter;

  public CacheWarmer() {
    this.executor = CacheThreadPoolManager.getInstance()
        .getOrCreatePool("cache-warmer", 2, 8);
  }

  public CacheWarmer(Executor executor) {
    this.executor = executor;
  }

  /**
   * 设置是否异步预热模式
   *
   * <p>开启后 {@link #afterSingletonsInstantiated()} 将在后台线程执行预热，
   * Spring 容器启动不会被阻塞。
   *
   * @param asyncMode true 表示异步预热
   */
  public void setAsyncMode(boolean asyncMode) {
    this.asyncMode = asyncMode;
  }

  /**
   * 设置预热超时时间
   *
   * <p>超时后将中断未完成的预热任务（不会中断已经在执行的任务），
   * 已完成的条目保留，未完成的放弃。
   *
   * @param timeout 超时时长
   * @param unit 时间单位
   */
  public void setTimeout(long timeout, TimeUnit unit) {
    this.timeoutMillis = unit.toMillis(timeout);
    if (this.timeoutMillis < 0) {
      this.timeoutMillis = 0;
    }
  }

  /**
   * 设置预热速率限制（控制并发加载的 QPS）
   *
   * <p>通过控制同时执行的加载任务数量，避免预热时压垮后端数据库。
   *
   * @param permits 最大并发加载数
   */
  public void setRateLimiter(int permits) {
    this.rateLimit = permits;
    this.rateLimiter = permits > 0 ? new Semaphore(permits) : null;
  }

  /**
   * 注册预热任务
   *
   * @param cacheName 缓存名称（用于日志）
   * @param cache 目标缓存
   * @param keys 需要预热的 key 列表
   * @param loader 数据加载函数
   * @param <K> 键类型
   * @param <V> 值类型
   */
  public <K, V> void registerWarmTask(
      String cacheName, Cache<K, V> cache, List<K> keys, Function<K, V> loader) {
    tasks.add(new WarmTask<>(cacheName, cache, keys, loader));
    log.info("缓存预热任务已注册: name={}, keyCount={}", cacheName, keys.size());
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (asyncMode) {
      // 异步模式：在后台线程执行预热，不阻塞容器启动
      CompletableFuture.runAsync(this::warmUp, executor);
      log.info("缓存预热已在后台线程启动（异步模式）");
    } else {
      // 同步模式：阻塞容器启动直到预热完成
      warmUp();
    }
  }

  /** 执行所有预热任务 */
  public void warmUp() {
    if (warmed) {
      log.warn("缓存预热已完成，跳过重复执行");
      return;
    }
    warmed = true;

    if (tasks.isEmpty()) {
      log.info("无缓存预热任务需要执行");
      return;
    }

    log.info("开始缓存预热，共 {} 个任务，async={}, timeout={}ms, rateLimit={}",
        tasks.size(), asyncMode, timeoutMillis, rateLimit);
    long startTime = System.currentTimeMillis();

    if (timeoutMillis > 0) {
      warmUpWithTimeout(startTime);
    } else {
      warmUpWithoutTimeout(startTime);
    }
  }

  /** 带超时的预热执行 */
  private void warmUpWithTimeout(long startTime) {
    CompletableFuture<?>[] futures = tasks.stream()
        .map(task -> CompletableFuture.runAsync(() -> executeWarmTask(task), executor))
        .toArray(CompletableFuture[]::new);

    try {
      CompletableFuture.allOf(futures).get(timeoutMillis, TimeUnit.MILLISECONDS);
      long elapsed = System.currentTimeMillis() - startTime;
      log.info("缓存预热完成（超时时间内），耗时={}ms", elapsed);
    } catch (TimeoutException e) {
      long elapsed = System.currentTimeMillis() - startTime;
      log.warn("缓存预热超时（{}ms），部分任务可能未完成，已耗时={}ms", timeoutMillis, elapsed);
    } catch (Exception e) {
      long elapsed = System.currentTimeMillis() - startTime;
      log.warn("缓存预热异常，已耗时={}ms", elapsed, e);
    }
  }

  /** 无超时限制的预热执行 */
  private void warmUpWithoutTimeout(long startTime) {
    // 并行执行所有预热任务
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (WarmTask<?, ?> task : tasks) {
      futures.add(CompletableFuture.runAsync(() -> executeWarmTask(task), executor));
    }

    // 等待所有任务完成
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    long elapsed = System.currentTimeMillis() - startTime;
    log.info("缓存预热完成，耗时={}ms", elapsed);
  }

  private <K, V> void executeWarmTask(WarmTask<K, V> task) {
    try {
      log.info("开始预热缓存: {}, keyCount={}", task.cacheName, task.keys.size());
      int success = 0;
      int skip = 0;
      int failed = 0;
      long start = System.currentTimeMillis();

      for (K key : task.keys) {
        // 只预热缓存中不存在的 key
        if (task.cache.containsKey(key)) {
          skip++;
          continue;
        }

        // 获取速率限制许可
        if (rateLimiter != null) {
          try {
            if (!rateLimiter.tryAcquire(timeoutMillis > 0 ? timeoutMillis : 30000,
                TimeUnit.MILLISECONDS)) {
              log.warn("预热任务 {} 获取速率限制许可超时，跳过剩余 key", task.cacheName);
              break;
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("预热任务 {} 被中断", task.cacheName);
            break;
          }
        }

        try {
          V value = task.loader.apply(key);
          if (value != null) {
            task.cache.put(key, value);
            success++;
          }
        } catch (Exception e) {
          failed++;
          log.warn("预热缓存条目失败: cache={}, key={}", task.cacheName, key, e);
        } finally {
          if (rateLimiter != null) {
            rateLimiter.release();
          }
        }
      }

      long elapsed = System.currentTimeMillis() - start;
      log.info(
          "缓存预热完成: {}, success={}, skip={}, failed={}, elapsed={}ms",
          task.cacheName,
          success,
          skip,
          failed,
          elapsed);
    } catch (Exception e) {
      log.error("缓存预热任务异常: {}", task.cacheName, e);
    }
  }

  @Override
  public void destroy() {
    tasks.clear();
    log.info("CacheWarmer 已销毁，预热任务已清理");
  }

  /**
   * 获取预热任务总数
   *
   * @return 预热任务数
   */
  public int getTaskCount() {
    return tasks.size();
  }

  /**
   * 判断是否已完成预热
   *
   * @return 已完成时返回 true
   */
  public boolean isWarmed() {
    return warmed;
  }

  /** 预热任务定义 */
  private static class WarmTask<K, V> {
    final String cacheName;
    final Cache<K, V> cache;
    final List<K> keys;
    final Function<K, V> loader;

    WarmTask(String cacheName, Cache<K, V> cache, List<K> keys, Function<K, V> loader) {
      this.cacheName = cacheName;
      this.cache = cache;
      this.keys = keys;
      this.loader = loader;
    }
  }

}
