package com.njydsz.common.cache.spring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.CacheManager;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.internal.loading.EnhancedLoadingCache;

/**
 * YdszCache 的 Spring CacheManager 实现
 *
 * <p>支持 per-cache 独立配置，每个缓存可以使用不同的类型、容量和过期策略。
 *
 * <p>生命周期管理：
 *
 * <ul>
 *   <li>实现 {@link DisposableBean}，在 Spring 容器关闭时自动清理资源
 *   <li>实现 {@link InitializingBean}，启动期预创建 {@code cacheNames} 配置的缓存
 *   <li>关闭所有 {@link EnhancedLoadingCache} 实例和共享线程池
 * </ul>
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * @Configuration
 * @EnableCaching
 * public class CacheConfig {
 *     @Bean
 *     public CacheManager cacheManager() {
 *         YdszCacheManager cacheManager = new YdszCacheManager();
 *         cacheManager.setCacheType(CacheType.TINYLFU);
 *         cacheManager.setMaximumSize(1000);
 *         cacheManager.setExpireAfterWrite(30, TimeUnit.MINUTES);
 *         return cacheManager;
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class YdszCacheManager implements CacheManager, DisposableBean, InitializingBean {

  private static final Logger LOG = LoggerFactory.getLogger(YdszCacheManager.class);

  private final Map<String, SpringYdszCache> cacheMap = new ConcurrentHashMap<>();

  /** 已创建的底层 Cache 实例（用于生命周期管理） */
  private final List<Cache<?, ?>> createdCaches = Collections.synchronizedList(new ArrayList<>());

  private Collection<String> cacheNames;

  private CacheType cacheType = CacheType.TINYLFU;

  private long maximumSize = 1000;

  private long expireAfterWrite = 0;

  private TimeUnit expireAfterWriteTimeUnit = TimeUnit.MINUTES;

  private int initialCapacity = 64;

  private boolean allowNullValues = true;

  private boolean recordStats = true;

  private long expireAfterAccess = 0;

  private TimeUnit expireAfterAccessTimeUnit = TimeUnit.MINUTES;

  private long refreshAfterWrite = 0;

  private TimeUnit refreshAfterWriteTimeUnit = TimeUnit.MINUTES;

  private Function<String, Cache<Object, Object>> cacheBuilder;

  /** per-cache 配置映射 */
  private Map<String, YdszCacheProperties.CacheConfig> perCacheConfigs = Collections.emptyMap();

  /** 设置缓存类型 */
  public void setCacheType(CacheType cacheType) {
    this.cacheType = cacheType;
  }

  /** 设置最大容量 */
  public void setMaximumSize(long maximumSize) {
    this.maximumSize = maximumSize;
  }

  /** 设置写入后过期时间 */
  public void setExpireAfterWrite(long duration, TimeUnit timeUnit) {
    this.expireAfterWrite = duration;
    this.expireAfterWriteTimeUnit = timeUnit;
  }

  /** 设置初始容量 */
  public void setInitialCapacity(int initialCapacity) {
    this.initialCapacity = initialCapacity;
  }

  /** 设置是否允许 null 值 */
  public void setAllowNullValues(boolean allowNullValues) {
    this.allowNullValues = allowNullValues;
  }

  /** 设置是否启用统计 */
  public void setRecordStats(boolean recordStats) {
    this.recordStats = recordStats;
  }

  /** 设置访问后过期时间 */
  public void setExpireAfterAccess(long duration, TimeUnit timeUnit) {
    this.expireAfterAccess = duration;
    this.expireAfterAccessTimeUnit = timeUnit;
  }

  /** 设置刷新间隔 */
  public void setRefreshAfterWrite(long duration, TimeUnit timeUnit) {
    this.refreshAfterWrite = duration;
    this.refreshAfterWriteTimeUnit = timeUnit;
  }

  /** 设置预定义的缓存名称 */
  public void setCacheNames(Collection<String> cacheNames) {
    this.cacheNames = cacheNames;
  }

  /** 设置自定义缓存构建器 */
  public void setCacheBuilder(Function<String, Cache<Object, Object>> cacheBuilder) {
    this.cacheBuilder = cacheBuilder;
  }

  /**
   * 设置 per-cache 配置映射
   *
   * @param perCacheConfigs per-cache 配置
   */
  public void setPerCacheConfigs(Map<String, YdszCacheProperties.CacheConfig> perCacheConfigs) {
    this.perCacheConfigs = perCacheConfigs != null ? perCacheConfigs : Collections.emptyMap();
  }

  @Override
  public SpringYdszCache getCache(String name) {
    SpringYdszCache cache = this.cacheMap.get(name);
    if (cache != null) {
      return cache;
    }

    // cacheNames 仅作为启动期预创建列表，不作为白名单拦截（空列表不得导致缓存整体失效）
    Cache<Object, Object> delegate = buildCache(name);
    createdCaches.add(delegate);
    SpringYdszCache newCache = new SpringYdszCache(name, delegate, this.allowNullValues);

    SpringYdszCache existing = this.cacheMap.putIfAbsent(name, newCache);
    return existing != null ? existing : newCache;
  }

  @Override
  public Collection<String> getCacheNames() {
    Set<String> names = new HashSet<>(this.cacheMap.keySet());
    if (this.cacheNames != null) {
      names.addAll(this.cacheNames);
    }
    return Collections.unmodifiableSet(names);
  }

  /**
   * Spring 容器初始化完成后预创建配置的缓存名称列表。
   *
   * <p>所有 setter 配置就绪后触发，确保预创建使用最终的全局与 per-cache 配置。
   */
  @Override
  public void afterPropertiesSet() {
    if (this.cacheNames == null || this.cacheNames.isEmpty()) {
      return;
    }
    for (final String name : this.cacheNames) {
      getCache(name);
    }
  }

  /** 构建底层 YdszCache 实例（支持 per-cache 配置覆盖） */
  private Cache<Object, Object> buildCache(String name) {
    if (this.cacheBuilder != null) {
      return this.cacheBuilder.apply(name);
    }

    // 获取 per-cache 配置（如有）
    YdszCacheProperties.CacheConfig perCache = perCacheConfigs.get(name);

    // 解析有效配置值（per-cache 优先，回退到全局默认）
    CacheType effectiveType =
        perCache != null && perCache.getType() != null ? perCache.getType() : this.cacheType;
    long effectiveMaxSize =
        perCache != null && perCache.getMaximumSize() != null
            ? perCache.getMaximumSize()
            : this.maximumSize;
    int effectiveInitCapacity =
        perCache != null && perCache.getInitialCapacity() != null
            ? perCache.getInitialCapacity()
            : this.initialCapacity;
    long effectiveExpireAfterWrite =
        perCache != null && perCache.getExpireAfterWrite() != null
            ? perCache.getExpireAfterWrite()
            : this.expireAfterWrite;
    long effectiveExpireAfterAccess =
        perCache != null && perCache.getExpireAfterAccess() != null
            ? perCache.getExpireAfterAccess()
            : this.expireAfterAccess;
    long effectiveRefreshAfterWrite =
        perCache != null && perCache.getRefreshAfterWrite() != null
            ? perCache.getRefreshAfterWrite()
            : this.refreshAfterWrite;
    TimeUnit effectiveWriteTimeUnit =
        perCache != null && perCache.getExpireTimeUnit() != null
            ? perCache.getExpireTimeUnit()
            : this.expireAfterWriteTimeUnit;
    TimeUnit effectiveAccessTimeUnit =
        perCache != null && perCache.getExpireTimeUnit() != null
            ? perCache.getExpireTimeUnit()
            : this.expireAfterAccessTimeUnit;
    TimeUnit effectiveRefreshTimeUnit =
        perCache != null && perCache.getExpireTimeUnit() != null
            ? perCache.getExpireTimeUnit()
            : this.refreshAfterWriteTimeUnit;
    boolean effectiveRecordStats =
        perCache != null && perCache.getRecordStats() != null
            ? perCache.getRecordStats()
            : this.recordStats;

    CacheBuilder<Object, Object> builder =
        YdszCache.newBuilder()
            .type(effectiveType)
            .initialCapacity(effectiveInitCapacity)
            .maximumSize(effectiveMaxSize)
            .recordStats(effectiveRecordStats);

    if (effectiveExpireAfterWrite > 0) {
      builder.expireAfterWrite(effectiveExpireAfterWrite, effectiveWriteTimeUnit);
    }
    if (effectiveExpireAfterAccess > 0) {
      builder.expireAfterAccess(effectiveExpireAfterAccess, effectiveAccessTimeUnit);
    }
    if (effectiveRefreshAfterWrite > 0) {
      builder.refreshAfterWrite(effectiveRefreshAfterWrite, effectiveRefreshTimeUnit);
    }

    LOG.debug(
        "构建缓存: name={}, type={}, maxSize={}, expireAfterWrite={} {}",
        name,
        effectiveType,
        effectiveMaxSize,
        effectiveExpireAfterWrite,
        effectiveWriteTimeUnit);

    return builder.build();
  }

  /** Spring 容器关闭时清理资源 */
  @Override
  public void destroy() {
    LOG.info("YdszCacheManager 正在关闭...");

    // 关闭所有可关闭的缓存实例
    synchronized (createdCaches) {
      for (Cache<?, ?> cache : createdCaches) {
        if (cache instanceof AutoCloseable) {
          try {
            ((AutoCloseable) cache).close();
          } catch (Exception e) {
            LOG.warn("关闭缓存实例失败: {}", cache.getClass().getSimpleName(), e);
          }
        }
      }
      createdCaches.clear();
    }

    cacheMap.clear();

    // 关闭 EnhancedLoadingCache 共享资源
    EnhancedLoadingCache.shutdownSharedResources();

    LOG.info("YdszCacheManager 已关闭");
  }
}
