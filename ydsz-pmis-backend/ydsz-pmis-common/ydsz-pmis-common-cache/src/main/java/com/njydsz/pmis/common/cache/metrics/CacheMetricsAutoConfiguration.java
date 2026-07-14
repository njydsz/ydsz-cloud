package com.njydsz.pmis.common.cache.metrics;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.cache.spring.YdszCacheManager;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 缓存可观测性自动配置
 *
 * <p>当 classpath 中存在 Micrometer 和 YdszCacheManager 时， 自动为每个缓存注册 {@link CacheMeterBinder} 指标（含
 * P50/P90/P99 分位数 Timer）。
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
  public List<MeterBinder> ydszCacheMeterBinders(YdszCacheManager cacheManager) {
    return cacheManager.getCacheNames().stream()
        .map(cacheManager::getCache)
        .map(
            springCache ->
                (MeterBinder)
                    new CacheMeterBinder(springCache.getNativeCache(), springCache.getName()))
        .toList();
  }
}
