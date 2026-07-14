package com.njydsz.pmis.common.cache.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * 缓存线程池统一管理器 — 集中管理缓存相关的所有线程池
 *
 * <p>解决各缓存组件各自创建线程池导致的资源浪费和管理困难。 统一管理以下线程池：
 *
 * <ul>
 *   <li>refreshPool：缓存自动刷新线程池
 *   <li>cleanupPool：过期清理线程池
 *   <li>listenerPool：异步删除监听器线程池
 *   <li>swrPool：SWR 异步重新加载线程池
 * </ul>
 *
 * <p>实现 {@link DisposableBean} 确保应用关闭时优雅关闭所有线程池。
 *
 * @author Marvin Lee
 * @version 4.1.0
 */
public class CacheThreadPoolManager implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(CacheThreadPoolManager.class);

  private final ConcurrentHashMap<String, ExecutorService> pools = new ConcurrentHashMap<>();

  /** 默认线程池大小 */
  private static final int DEFAULT_POOL_SIZE =
      Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

  /** 创建或获取指定名称的线程池 */
  public ExecutorService getOrCreatePool(String name) {
    return getOrCreatePool(name, DEFAULT_POOL_SIZE, DEFAULT_POOL_SIZE * 2);
  }

  /** 创建或获取指定名称和配置的线程池 */
  public ExecutorService getOrCreatePool(String name, int coreSize, int maxSize) {
    return pools.computeIfAbsent(name, n -> createPool(n, coreSize, maxSize));
  }

  /** 创建缓存专用线程池 */
  private ExecutorService createPool(String name, int coreSize, int maxSize) {
    ThreadFactory factory =
        new ThreadFactory() {
          private final AtomicInteger counter = new AtomicInteger(0);

          @Override
          public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ydsz-cache-" + name + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
          }
        };

    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            factory,
            new ThreadPoolExecutor.CallerRunsPolicy());

    log.info("缓存线程池已创建: name={}, coreSize={}, maxSize={}", name, coreSize, maxSize);
    return executor;
  }

  /** 关闭指定线程池 */
  public void shutdownPool(String name) {
    ExecutorService pool = pools.remove(name);
    if (pool != null) {
      gracefulShutdown(pool, name);
    }
  }

  @Override
  public void destroy() {
    log.info("正在关闭所有缓存线程池，共 {} 个", pools.size());
    pools.forEach((name, pool) -> gracefulShutdown(pool, name));
    pools.clear();
  }

  /** 优雅关闭线程池 */
  private void gracefulShutdown(ExecutorService pool, String name) {
    pool.shutdown();
    try {
      if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
          log.warn("缓存线程池未能完全关闭: {}", name);
        }
      }
    } catch (InterruptedException e) {
      pool.shutdownNow();
      Thread.currentThread().interrupt();
    }
    log.info("缓存线程池已关闭: {}", name);
  }

  /** 获取所有线程池的状态信息 */
  public String getPoolStatus() {
    StringBuilder sb = new StringBuilder();
    pools.forEach(
        (name, pool) -> {
          if (pool instanceof ThreadPoolExecutor tpe) {
            sb.append(String.format(
                "%s: active=%d, core=%d, max=%d, queue=%d, completed=%d%n",
                name,
                tpe.getActiveCount(),
                tpe.getCorePoolSize(),
                tpe.getMaximumPoolSize(),
                tpe.getQueue().size(),
                tpe.getCompletedTaskCount()));
          }
        });
    return sb.toString();
  }
}
