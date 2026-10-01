package com.njydsz.common.cache.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import com.njydsz.common.thread.factory.InternalExecutorFactory;

/**
 * 缓存线程池统一管理器 — 集中管理缓存相关的所有线程池
 *
 * <p>解决各缓存组件各自创建线程池导致的资源浪费和管理困难。统一管理以下线程池：
 *
 * <ul>
 *   <li>refreshPool：缓存自动刷新线程池
 *   <li>cleanupPool：过期清理线程池
 *   <li>listenerPool：异步删除监听器线程池
 * </ul>
 *
 * <p>实现 {@link DisposableBean} 确保应用关闭时优雅关闭所有线程池。
 *
 * <p><b>变更记录：</b>自建 {@code new ThreadPoolExecutor} 逻辑已委托
 * {@link InternalExecutorFactory}（符合云顶编码规范 YDIZ-CONC-001/002），
 * 所有创建的线程池自动注册到 {@code com.njydsz.common.thread.registry.ThreadPoolRegistry}
 * 纳入统一监控、指标采集与 Actuator 端点查询。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public class CacheThreadPoolManager implements DisposableBean {

  private static final Logger LOG = LoggerFactory.getLogger(CacheThreadPoolManager.class);

  /** 全局单例实例（供非 Spring 管理的组件使用） */
  private static volatile CacheThreadPoolManager instance;

  /** 单例持有者（Initialization-on-demand holder idiom） */
  private static final class SingletonHolder {
    static final CacheThreadPoolManager LAZY = new CacheThreadPoolManager();
  }

  /**
   * 获取全局单例实例
   *
   * <p>供非 Spring 管理的缓存组件（如 ExpirableCache 等）使用，确保所有线程池统一管理。
   *
   * @return 全局单例实例
   */
  public static CacheThreadPoolManager getInstance() {
    if (instance == null) {
      instance = SingletonHolder.LAZY;
    }
    return instance;
  }

  /**
   * 设置全局单例实例（由 Spring 自动配置调用）
   *
   * <p>当 Spring 容器创建 CacheThreadPoolManager Bean 时，通过此方法替换静态单例，
   * 使 Spring 的生命周期管理（DisposableBean）生效。
   *
   * @param manager Spring 管理的 CacheThreadPoolManager 实例
   */
  public static void setInstance(CacheThreadPoolManager manager) {
    instance = manager;
  }

  /** 普通线程池本地引用缓存（避免重复创建，同名首次调用时新建，后续复用同一实例） */
  private final ConcurrentHashMap<String, ExecutorService> pools = new ConcurrentHashMap<>(16);

  /** 定时调度线程池本地引用缓存 */
  private final ConcurrentHashMap<String, ScheduledExecutorService> scheduledPools =
      new ConcurrentHashMap<>(16);

  /** 默认线程池大小 */
  private static final int DEFAULT_POOL_SIZE =
      Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

  /** 默认队列容量 */
  private static final int DEFAULT_QUEUE_CAPACITY = 1024;

  /**
   * 创建或获取指定名称的线程池
   *
   * @param name 名称
   * @return 与 {@code name} 绑定的线程池，不会为 {@code null}；同名首次调用时新建，后续复用同一实例。
   *     规模固定为默认核心数（CPU 核数的一半，最小 2）与两倍最大线程数
   */
  public ExecutorService getOrCreatePool(String name) {
    return getOrCreatePool(name, DEFAULT_POOL_SIZE, DEFAULT_POOL_SIZE * 2);
  }

  /**
   * 创建或获取指定名称和配置的线程池
   *
   * @param name 名称
   * @param coreSize coreSize 参数
   * @param maxSize maxSize 参数
   * @return 与 {@code name} 绑定的线程池，不会为 {@code null}；同名已存在时直接返回既有实例，
   *     此时 {@code coreSize} 与 {@code maxSize} 不生效
   */
  public ExecutorService getOrCreatePool(String name, int coreSize, int maxSize) {
    return pools.computeIfAbsent(
        name,
        n ->
            InternalExecutorFactory.newCustomThreadPool(
                "cache-" + n,
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY)));
  }

  /**
   * 创建或获取指定名称的定时调度线程池
   *
   * @param name 线程池名称
   * @param coreSize 核心线程数
   * @return 定时调度线程池
   */
  public ScheduledExecutorService getOrCreateScheduledPool(String name, int coreSize) {
    return scheduledPools.computeIfAbsent(
        name, n -> InternalExecutorFactory.newScheduledThreadPool("cache-" + n, coreSize));
  }

  /**
   * 关闭指定线程池
   *
   * @param name 名称
   */
  public void shutdownPool(String name) {
    ExecutorService pool = pools.remove(name);
    if (pool != null) {
      gracefulShutdown(pool, name);
    }
    ScheduledExecutorService scheduledPool = scheduledPools.remove(name);
    if (scheduledPool != null) {
      gracefulShutdown(scheduledPool, name);
    }
  }

  @Override
  public void destroy() {
    LOG.info("正在关闭所有缓存线程池，共 {} 个普通池 + {} 个调度池", pools.size(), scheduledPools.size());
    pools.forEach((name, pool) -> gracefulShutdown(pool, name));
    scheduledPools.forEach((name, pool) -> gracefulShutdown(pool, name));
    pools.clear();
    scheduledPools.clear();
  }

  /**
   * 获取所有线程池的状态信息
   *
   * @return 多行状态文本，按池名逐行输出活跃线程数、核心/最大线程数、队列长度与已完成任务数；
   *     尚无任何线程池时返回空串
   */
  public String getPoolStatus() {
    StringBuilder sb = new StringBuilder();
    pools.forEach(
        (name, pool) -> {
          if (pool instanceof ThreadPoolExecutor tpe) {
            sb.append(
                String.format(
                    "%s: active=%d, core=%d, max=%d, queue=%d, completed=%d%n",
                    name,
                    tpe.getActiveCount(),
                    tpe.getCorePoolSize(),
                    tpe.getMaximumPoolSize(),
                    tpe.getQueue().size(),
                    tpe.getCompletedTaskCount()));
          }
        });
    scheduledPools.forEach(
        (name, pool) -> {
          if (pool instanceof ScheduledThreadPoolExecutor stpe) {
            sb.append(
                String.format(
                    "%s [scheduled]: active=%d, core=%d, queue=%d, completed=%d%n",
                    name,
                    stpe.getActiveCount(),
                    stpe.getCorePoolSize(),
                    stpe.getQueue().size(),
                    stpe.getCompletedTaskCount()));
          }
        });
    return sb.toString();
  }

  /** 优雅关闭线程池 */
  private void gracefulShutdown(ExecutorService pool, String name) {
    pool.shutdown();
    try {
      if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
          LOG.warn("缓存线程池未能完全关闭: {}", name);
        }
      }
    } catch (InterruptedException e) {
      pool.shutdownNow();
      Thread.currentThread().interrupt();
    }
    LOG.info("缓存线程池已关闭: {}", name);
  }
}
