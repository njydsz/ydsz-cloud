package com.njydsz.pmis.common.cache.spring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cache.CacheManager;

import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheBuilder;
import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.common.cache.internal.loading.EnhancedLoadingCache;

/**
 * YdszCache 的 Spring CacheManager 实现
 *
 * <p>支持 per-cache 独立配置，每个缓存可以使用不同的类型、容量和过期策略。
 *
 * <p>生命周期管理：
 *
 * <ul>
 *   <li>实现 {@link DisposableBean}，在 Spring 容器关闭时自动清理资源
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
 * @author ydsz-pmis-team
 * 
 */
public class YdszCacheManager implements CacheManager, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(YdszCacheManager.class);

  private final Map<String, SpringYdszCache> cacheMap = new ConcurrentHashMap<>();

  /** 已创建的底层 Cache 实例（用于生命周期管理） */
  private final List<Cache<?, ?>> createdCaches = Collections.synchronizedList(new ArrayList<>());

  private Collection<String> cacheNames;

  private CacheType cacheType = CacheType.TINYLFU;

  private long maximumSize = 1000;

  private long expireAfterWrite = 0;

  private TimeUnit expireTimeUnit = TimeUnit.MINUTES;

  private int initialCapacity = 64;

  private boolean allowNullValues = true;

  private boolean recordStats = true;

  private long expireAfterAccess = 0;

  private long refreshAfterWrite = 0;

  private boolean weakKeys = false;

  private boolean weakValues = false;

  private boolean softValues = false;

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
    this.expireTimeUnit = timeUnit;
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
    this.expireTimeUnit = timeUnit;
  }

  /** 设置刷新间隔 */
  public void setRefreshAfterWrite(long duration, TimeUnit timeUnit) {
    this.refreshAfterWrite = duration;
    this.expireTimeUnit = timeUnit;
  }

  /** 设置是否使用弱引用键 */
  public void setWeakKeys(boolean weakKeys) {
    this.weakKeys = weakKeys;
  }

  /** 设置是否使用弱引用值 */
  public void setWeakValues(boolean weakValues) {
    this.weakValues = weakValues;
  }

  /** 设置是否使用软引用值 */
  public void setSoftValues(boolean softValues) {
    this.softValues = softValues;
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

    if (this.cacheNames != null && !this.cacheNames.contains(name)) {
      return null;
    }

    Cache<Object, Object> delegate = buildCache(name);
    createdCaches.add(delegate);
        SpringYdszCache newCache = new SpringYdszCache(name, delegate, this.allowNullValues);

    SpringYdszCache existing = this.cacheMap.putIfAbsent(name, newCache);
    return existing != null ? existing : newCache;
  }

  @Override
    public Collection<String> getCacheNames() {
    return Collections.unmodifiableSet(this.cacheMap.keySet());
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
    TimeUnit effectiveTimeUnit =
        perCache != null && perCache.getExpireTimeUnit() != null
            ? perCache.getExpireTimeUnit()
            : this.expireTimeUnit;
    boolean effectiveRecordStats =
        perCache != null && perCache.getRecordStats() != null
            ? perCache.getRecordStats()
            : this.recordStats;
    boolean effectiveWeakKeys =
        perCache != null && perCache.getWeakKeys() != null ? perCache.getWeakKeys() : this.weakKeys;
    boolean effectiveWeakValues =
        perCache != null && perCache.getWeakValues() != null
            ? perCache.getWeakValues()
            : this.weakValues;
    boolean effectiveSoftValues =
        perCache != null && perCache.getSoftValues() != null
            ? perCache.getSoftValues()
            : this.softValues;

    CacheBuilder<Object, Object> builder =
        YdszCache.newBuilder()
            .type(effectiveType)
            .initialCapacity(effectiveInitCapacity)
            .maximumSize(effectiveMaxSize)
            .recordStats(effectiveRecordStats);

    if (effectiveWeakKeys) {
      builder.weakKeys();
    }
    if (effectiveWeakValues) {
      builder.weakValues();
    }
    if (effectiveSoftValues) {
      builder.softValues();
    }

    if (effectiveExpireAfterWrite > 0) {
      builder.expireAfterWrite(effectiveExpireAfterWrite, effectiveTimeUnit);
    }
    if (effectiveExpireAfterAccess > 0) {
      builder.expireAfterAccess(effectiveExpireAfterAccess, effectiveTimeUnit);
    }
    if (effectiveRefreshAfterWrite > 0) {
      builder.refreshAfterWrite(effectiveRefreshAfterWrite, effectiveTimeUnit);
    }

    log.debug(
        "构建缓存: name={}, type={}, maxSize={}, expireAfterWrite={} {}",
        name,
        effectiveType,
        effectiveMaxSize,
        effectiveExpireAfterWrite,
        effectiveTimeUnit);

    return builder.build();
  }

  /** Spring 容器关闭时清理资源 */
  @Override
  public void destroy() {
    log.info("YdszCacheManager 正在关闭...");

    // 关闭所有可关闭的缓存实例
    synchronized (createdCaches) {
      for (Cache<?, ?> cache : createdCaches) {
        if (cache instanceof AutoCloseable) {
          try {
            ((AutoCloseable) cache).close();
          } catch (Exception e) {
            log.warn("关闭缓存实例失败: {}", cache.getClass().getSimpleName(), e);
          }
        }
      }
      createdCaches.clear();
    }

    cacheMap.clear();

    // 关闭 EnhancedLoadingCache 共享资源
    EnhancedLoadingCache.shutdownSharedResources();

    log.info("YdszCacheManager 已关闭");
  }
}
