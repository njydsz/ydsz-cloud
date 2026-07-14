package com.njydsz.pmis.common.cache.metrics;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.cache.spring.YdszCacheManager;
import com.njydsz.pmis.common.cache.spring.SpringYdszCache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 缓存可观测性自动配置
 *
 * <p>当 classpath 中存在 Micrometer 和 YdszCacheManager 时， 自动为每个缓存注册 {@link CacheMeterBinder} 指标（含
 * P50/P90/P99 分位数 Timer）。
 *
 * <p>动态绑定：实现 {@link SmartInitializingSingleton}，在所有单例 Bean 初始化完成后绑定已有缓存的指标，
 * 并提供 {@link CacheMetricsRegistrar#registerCache(String)} 方法支持运行时动态注册新创建的缓存。
 *
 * <p>注册的指标：
 *
 * <ul>
 *   <li>cache.size - 当前缓存条目数（Gauge）
 *   <li>cache.gets - 缓存查询总次数（FunctionCounter）
 *   <li>cache.misses - 缓存未命中总次数（FunctionCounter）
 *   <li>cache.puts - 缓存加载放入总次数（FunctionCounter）
 *   <li>cache.hit.rate - 缓存命中率（Gauge，0.0 ~ 1.0）
 *   <li>cache.evictions - 淘汰总次数（FunctionCounter）
 *   <li>cache.load.duration - 平均加载耗时（FunctionTimer）
 *   <li>cache.get.duration - GET 操作耗时分布（Timer，含 P50/P90/P99）
 *   <li>cache.put.duration - PUT 操作耗时分布（Timer，含 P50/P90/P99）
 * </ul>
 *
 * @author ydsz-pmis-team
 * 
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean({MeterRegistry.class, YdszCacheManager.class})
public class CacheMetricsAutoConfiguration {

  @Bean
  public CacheMetricsRegistrar cacheMetricsRegistrar(
      YdszCacheManager cacheManager, MeterRegistry meterRegistry) {
    return new CacheMetricsRegistrar(cacheManager, meterRegistry);
  }

  /**
   * 缓存指标注册器 — 支持动态绑定新创建的缓存
   */
  public static class CacheMetricsRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(CacheMetricsRegistrar.class);

    private final YdszCacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final Set<String> registeredCacheNames = ConcurrentHashMap.newKeySet();

    CacheMetricsRegistrar(YdszCacheManager cacheManager, MeterRegistry meterRegistry) {
      this.cacheManager = cacheManager;
      this.meterRegistry = meterRegistry;
    }

    @Override
    public void afterSingletonsInstantiated() {
      // 绑定所有启动时已存在的缓存
      for (String cacheName : cacheManager.getCacheNames()) {
        registerCache(cacheName);
      }
      log.info("CacheMetricsRegistrar 已初始化, 已绑定 {} 个缓存的指标", registeredCacheNames.size());
    }

    /**
     * 动态注册缓存指标（用于运行时新创建的缓存）
     *
     * @param cacheName 缓存名称
     */
    public void registerCache(String cacheName) {
      if (registeredCacheNames.contains(cacheName)) {
        return;
      }
      SpringYdszCache springCache = cacheManager.getCache(cacheName);
      if (springCache == null) {
        return;
      }
      MeterBinder binder =
          new CacheMeterBinder(springCache.getNativeCache(), springCache.getName());
      binder.bindTo(meterRegistry);
      registeredCacheNames.add(cacheName);
      log.debug("已注册缓存指标: {}", cacheName);
    }
  }
}
