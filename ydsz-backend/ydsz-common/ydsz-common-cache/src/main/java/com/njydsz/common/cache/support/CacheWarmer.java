package com.njydsz.common.cache.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import com.njydsz.common.cache.api.Cache;

/**
 * 缓存预热器 — Spring 生命周期管理
 *
 * <p>在 Spring 容器初始化完成后自动执行缓存预热， 在应用完全启动前将热点数据加载到缓存中，
 * 避免启动初期的缓存穿透和后端压力骤增。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * @Bean
 * public CacheWarmer cacheWarmer() {
 *   CacheWarmer warmer = new CacheWarmer();
 *   warmer.registerWarmTask("userCache", userCache, userIds, this::loadUser);
 *   warmer.registerWarmTask("configCache", configCache, configKeys, this::loadConfig);
 *   return warmer;
 * }
 * }</pre>
 *
 * <p>实现 {@link SmartInitializingSingleton} 确保在所有 Bean 初始化完成后执行预热。
 * 实现 {@link DisposableBean} 确保应用关闭时清理资源。
 *
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CacheWarmer implements SmartInitializingSingleton, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(CacheWarmer.class);

  private final List<WarmTask<?, ?>> tasks = new ArrayList<>();
  private final Executor executor;
  private volatile boolean warmed = false;

  public CacheWarmer() {
    this(ForkJoinPool.commonPool());
  }

  public CacheWarmer(Executor executor) {
    this.executor = executor != null ? executor : ForkJoinPool.commonPool();
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
    warmUp();
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

    log.info("开始缓存预热，共 {} 个任务", tasks.size());
    long startTime = System.currentTimeMillis();

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
      long start = System.currentTimeMillis();

      for (K key : task.keys) {
        // 只预热缓存中不存在的 key
        if (task.cache.containsKey(key)) {
          skip++;
          continue;
        }
        try {
          V value = task.loader.apply(key);
          if (value != null) {
            task.cache.put(key, value);
            success++;
          }
        } catch (Exception e) {
          log.warn("预热缓存条目失败: cache={}, key={}", task.cacheName, key, e);
        }
      }

      long elapsed = System.currentTimeMillis() - start;
      log.info(
          "缓存预热完成: {}, success={}, skip={}, failed={}, elapsed={}ms",
          task.cacheName,
          success,
          skip,
          task.keys.size() - success - skip,
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
