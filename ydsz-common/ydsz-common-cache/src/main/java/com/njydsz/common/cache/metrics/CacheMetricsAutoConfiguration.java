package com.njydsz.common.cache.metrics;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.cache.spring.SpringYdszCache;
import com.njydsz.common.cache.spring.YdszCacheManager;

/**
 * 缓存可观测性自动配置
 *
 * <p>当 classpath 中存在 Micrometer 和 YdszCacheManager 时， 自动为每个缓存注册 {@link CacheMeterBinder} 指标（含
 * P50/P90/P99 分位数 Timer）。
 *
 * <p>动态绑定：实现 {@link SmartInitializingSingleton}，在所有单例 Bean 初始化完成后绑定已有缓存的指标， 并提供 {@link
 * CacheMetricsRegistrar#registerCache(String)} 方法支持运行时动态注册新创建的缓存。
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
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean({MeterRegistry.class, YdszCacheManager.class})
public class CacheMetricsAutoConfiguration {

  /**
   * 注册缓存指标绑定器，将 YdszCacheManager 管理的缓存暴露为 Micrometer 指标。
   *
   * <p>由于用户在运行时可能动态创建新缓存，普通静态绑定无法覆盖，故返回 {@link CacheMetricsRegistrar} 这一 {@link
   * SmartInitializingSingleton}：它在所有单例 Bean 就绪后被动绑定已存在缓存，并对外提供 {@code registerCache} 供运行时动态注册。仅在
   * classpath 同时存在 {@code MeterRegistry} 与 {@code YdszCacheManager} 时装配（见类级
   * {@code @ConditionalOnBean}），缺失监控依赖时整体不生效， 不影响缓存本身功能。
   *
   * @param cacheManager 缓存管理器，由 Spring 容器注入，不会为 null
   * @param meterRegistry Micrometer 注册中心，由 Spring 容器注入，不会为 null
   * @return 缓存指标注册器实例，交给 Spring 托管为单例
   */
  @Bean
  public CacheMetricsRegistrar cacheMetricsRegistrar(
      YdszCacheManager cacheManager, MeterRegistry meterRegistry) {
    return new CacheMetricsRegistrar(cacheManager, meterRegistry);
  }

  /** 缓存指标注册器 — 支持动态绑定新创建的缓存 */
  public static class CacheMetricsRegistrar implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(CacheMetricsRegistrar.class);

    private final YdszCacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final Set<String> registeredCacheNames = ConcurrentHashMap.newKeySet();

    CacheMetricsRegistrar(YdszCacheManager cacheManager, MeterRegistry meterRegistry) {
      this.cacheManager = cacheManager;
      this.meterRegistry = meterRegistry;
    }

    /**
     * 所有单例 Bean 初始化完成后，为启动阶段已存在的缓存统一绑定指标。
     *
     * <p>实现 {@link SmartInitializingSingleton} 保证在依赖缓存注册完毕后才执行， 避免因 Bean 创建顺序导致漏绑；运行时新建的缓存仍需显式调用
     * {@link #registerCache(String)}。
     */
    @Override
    public void afterSingletonsInstantiated() {
      // 绑定所有启动时已存在的缓存
      for (String cacheName : cacheManager.getCacheNames()) {
        registerCache(cacheName);
      }
      LOG.info("CacheMetricsRegistrar 已初始化, 已绑定 {} 个缓存的指标", registeredCacheNames.size());
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
      LOG.debug("已注册缓存指标: {}", cacheName);
    }
  }
}
