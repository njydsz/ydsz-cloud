package com.njydsz.pmis.common.cache.support;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.api.LoadingCache;
import com.njydsz.pmis.common.cache.stats.CacheStats;

/**
 * 缓存调度器 - 支持定时清理、自动刷新、过期检查
 *
 * <p>核心功能：
 *
 * <ul>
 *   <li>定时清理：定期检查并清理过期缓存项
 *   <li>自动刷新：定时刷新指定缓存项
 *   <li>容量检查：超出容量时自动淘汰
 *   <li>统计重置：定时重置统计数据
 *   <li>健康检查：监控缓存运行状态
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 创建调度器
 * CacheScheduler scheduler = new CacheScheduler();
 *
 * // 定时清理过期缓存（每分钟执行一次）
 * scheduler.scheduleCleanup(ttlCache, 1, TimeUnit.MINUTES);
 *
 * // 定时刷新热点缓存（每 5 分钟执行一次）
 * scheduler.scheduleRefresh(loadingCache, hotKeys, 5, TimeUnit.MINUTES);
 *
 * // 关闭调度器（应用退出时）
 * scheduler.shutdown();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class CacheScheduler {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(CacheScheduler.class);

  /** 调度器执行服务 */
  private final ScheduledExecutorService scheduler;

  /** 默认构造函数（使用守护线程） */
  public CacheScheduler() {
    this.scheduler =
        Executors.newScheduledThreadPool(
            2,
            r -> {
              Thread t = new Thread(r, "CacheScheduler");
              t.setDaemon(true);
              t.setPriority(Thread.NORM_PRIORITY - 1);
              return t;
            });
    log.info("缓存调度器已创建");
  }

  /**
   * 自定义调度器构造函数
   *
   * @param poolSize 线程池大小
   * @param threadName 线程名称前缀
   */
  public CacheScheduler(int poolSize, String threadName) {
    this.scheduler =
        Executors.newScheduledThreadPool(
            poolSize,
            r -> {
              Thread t = new Thread(r, threadName);
              t.setDaemon(true);
              t.setPriority(Thread.NORM_PRIORITY - 1);
              return t;
            });
    log.info("缓存调度器已创建，poolSize={}, threadName={}", poolSize, threadName);
  }

  /**
   * 使用外部调度器
   *
   * @param scheduler 外部调度器
   */
  public CacheScheduler(ScheduledExecutorService scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * 定时清理过期缓存项
   *
   * <p>适用于 TTLCache、WeakKeyCache、WeakValueCache 等支持 cleanup() 方法的缓存
   *
   * @param cache 缓存实例
   * @param interval 清理间隔
   * @param unit 时间单位
   */
  public void scheduleCleanup(Cache<?, ?> cache, long interval, TimeUnit unit) {
    scheduler.scheduleAtFixedRate(
        () -> {
          try {
            callCleanup(cache);
          } catch (Exception e) {
            log.warn("缓存清理任务执行异常", e);
          }
        },
        interval,
        interval,
        unit);
    log.info("定时清理任务已注册，interval={} {}", interval, unit);
  }

  /** 调用缓存的 cleanup 方法 */
  private void callCleanup(Cache<?, ?> cache) {
    try {
      cache.cleanUp();
    } catch (Exception e) {
      log.warn("调用 cleanup 方法失败", e);
    }
  }

  /**
   * 定时打印缓存统计信息
   *
   * @param cache 缓存实例
   * @param interval 打印间隔
   * @param unit 时间单位
   */
  public void scheduleStatsPrinter(Cache<?, ?> cache, long interval, TimeUnit unit) {
    scheduler.scheduleAtFixedRate(
        () -> {
          try {
            double hitRate = cache.getHitRate();
            long size = cache.estimatedSize();
            log.info("缓存统计信息 - size={}, hitRate={}%", size, String.format("%.2f", hitRate * 100));
          } catch (Exception e) {
            log.warn("缓存统计打印任务执行异常", e);
          }
        },
        interval,
        interval,
        unit);
    log.info("定时统计打印任务已注册，interval={} {}", interval, unit);
  }

  /**
   * 定时刷新缓存项
   *
   * <p>适用于 LoadingCache 类型
   *
   * @param cache 加载缓存实例
   * @param keys 需要刷新的键集合
   * @param interval 刷新间隔
   * @param unit 时间单位
   */
  public <K> void scheduleRefresh(
      LoadingCache<K, ?> cache, Collection<K> keys, long interval, TimeUnit unit) {
    scheduler.scheduleAtFixedRate(
        () -> {
          try {
            for (K key : keys) {
              cache.refresh(key);
            }
            log.debug("缓存刷新任务执行完成，keys={}", keys.size());
          } catch (Exception e) {
            log.warn("缓存刷新任务执行异常", e);
          }
        },
        interval,
        interval,
        unit);
    log.info("定时刷新任务已注册，keys={}, interval={} {}", keys.size(), interval, unit);
  }

  /**
   * 定时检查缓存健康状态
   *
   * @param cache 缓存实例
   * @param maxSize 最大容量阈值
   * @param interval 检查间隔
   * @param unit 时间单位
   */
  public void scheduleHealthCheck(Cache<?, ?> cache, int maxSize, long interval, TimeUnit unit) {
    scheduler.scheduleAtFixedRate(
        () -> {
          try {
            long size = cache.estimatedSize();
            double usage = (double) size / maxSize * 100;
            if (usage > 90) {
              log.warn(
                  "缓存容量告警 - usage={}%, size={}/{}", String.format("%.1f", usage), size, maxSize);
            } else if (usage > 70) {
              log.info(
                  "缓存容量正常 - usage={}%, size={}/{}", String.format("%.1f", usage), size, maxSize);
            }
          } catch (Exception e) {
            log.warn("缓存健康检查任务执行异常", e);
          }
        },
        interval,
        interval,
        unit);
    log.info("定时健康检查任务已注册，maxSize={}, interval={} {}", maxSize, interval, unit);
  }

  /**
   * 定时重置缓存统计
   *
   * @param cache 缓存实例
   * @param interval 重置间隔
   * @param unit 时间单位
   */
  public void scheduleStatsReset(Cache<?, ?> cache, long interval, TimeUnit unit) {
    scheduler.scheduleAtFixedRate(
        () -> {
          try {
            CacheStats stats = cache.getStats();
            log.info(
                "缓存统计快照 - hitCount={}, missCount={}, hitRate={}%",
                stats.getHitCount(),
                stats.getMissCount(),
                String.format("%.2f", cache.getHitRate() * 100));
          } catch (Exception e) {
            log.warn("缓存统计重置任务执行异常", e);
          }
        },
        interval,
        interval,
        unit);
    log.info("定时统计快照任务已注册，interval={} {}", interval, unit);
  }

  /**
   * 延迟执行一次性清理任务
   *
   * @param cache 缓存实例
   * @param delay 延迟时间
   * @param unit 时间单位
   */
  public void scheduleOneTimeCleanup(Cache<?, ?> cache, long delay, TimeUnit unit) {
    scheduler.schedule(
        () -> {
          try {
            callCleanup(cache);
            log.info("一次性清理任务执行完成");
          } catch (Exception e) {
            log.warn("一次性清理任务执行异常", e);
          }
        },
        delay,
        unit);
    log.info("一次性清理任务已注册，delay={} {}", delay, unit);
  }

  /** 关闭调度器 */
  public void shutdown() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
        log.info("缓存调度器已关闭");
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
        log.warn("缓存调度器关闭中断", e);
      }
    }
  }

  /**
   * 获取调度器状态
   *
   * @return 调度器是否正在运行
   */
  public boolean isRunning() {
    return scheduler != null && !scheduler.isShutdown() && !scheduler.isTerminated();
  }
}
