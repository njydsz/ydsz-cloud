package com.njydsz.pmis.common.cache.spring;

import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheBuilder;
import com.njydsz.pmis.common.cache.builder.CacheType;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * YdszCache 的 Spring CacheManager 实现
 *
 * <p>类似 Caffeine 的 CaffeineCacheManager，提供 YdszCache 的 Spring 集成。
 * 支持 @Cacheable、@CachePut、@CacheEvict 等 Spring Cache 注解。
 *
 * <p>使用示例：
 * <pre>
 * {@code
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
 * }
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class YdszCacheManager implements CacheManager {

    private final Map<String, SpringYdszCache> cacheMap = new ConcurrentHashMap<>();

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

    /**
     * 设置缓存类型
     */
    public void setCacheType(CacheType cacheType) {
        this.cacheType = cacheType;
    }

    /**
     * 设置最大容量
     */
    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }

    /**
     * 设置写入后过期时间
     */
    public void setExpireAfterWrite(long duration, TimeUnit timeUnit) {
        this.expireAfterWrite = duration;
        this.expireTimeUnit = timeUnit;
    }

    /**
     * 设置初始容量
     */
    public void setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    /**
     * 设置是否允许 null 值
     */
    public void setAllowNullValues(boolean allowNullValues) {
        this.allowNullValues = allowNullValues;
    }

    /**
     * 设置是否启用统计
     */
    public void setRecordStats(boolean recordStats) {
        this.recordStats = recordStats;
    }

    /**
     * 设置访问后过期时间
     */
    public void setExpireAfterAccess(long duration, TimeUnit timeUnit) {
        this.expireAfterAccess = duration;
        this.expireTimeUnit = timeUnit;
    }

    /**
     * 设置刷新间隔
     */
    public void setRefreshAfterWrite(long duration, TimeUnit timeUnit) {
        this.refreshAfterWrite = duration;
        this.expireTimeUnit = timeUnit;
    }

    /**
     * 设置是否使用弱引用键
     */
    public void setWeakKeys(boolean weakKeys) {
        this.weakKeys = weakKeys;
    }

    /**
     * 设置是否使用弱引用值
     */
    public void setWeakValues(boolean weakValues) {
        this.weakValues = weakValues;
    }

    /**
     * 设置是否使用软引用值
     */
    public void setSoftValues(boolean softValues) {
        this.softValues = softValues;
    }

    /**
     * 设置预定义的缓存名称
     */
    public void setCacheNames(Collection<String> cacheNames) {
        this.cacheNames = cacheNames;
    }

    /**
     * 设置自定义缓存构建器
     */
    public void setCacheBuilder(Function<String, Cache<Object, Object>> cacheBuilder) {
        this.cacheBuilder = cacheBuilder;
    }

    @Override
    public SpringYdszCache getCache(@NonNull String name) {
        SpringYdszCache cache = this.cacheMap.get(name);
        if (cache != null) {
            return cache;
        }

        if (this.cacheNames != null && !this.cacheNames.contains(name)) {
            return null;
        }

        Cache<Object, Object> delegate = buildCache(name);
        @SuppressWarnings("null")
        SpringYdszCache newCache = new SpringYdszCache(name, delegate, this.allowNullValues);

        SpringYdszCache existing = this.cacheMap.putIfAbsent(name, newCache);
        return existing != null ? existing : newCache;
    }

    @Override
    @NonNull
    @SuppressWarnings("null")
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(this.cacheMap.keySet());
    }

    /**
     * 构建底层 YdszCache 实例
     */
    private Cache<Object, Object> buildCache(String name) {
        if (this.cacheBuilder != null) {
            return this.cacheBuilder.apply(name);
        }

        CacheBuilder<Object, Object> builder = YdszCache.newBuilder()
                .type(this.cacheType)
                .initialCapacity(this.initialCapacity)
                .maximumSize(this.maximumSize)
                .recordStats(this.recordStats);

        if (this.weakKeys) {
            builder.weakKeys();
        }
        if (this.weakValues) {
            builder.weakValues();
        }
        if (this.softValues) {
            builder.softValues();
        }

        if (this.expireAfterWrite > 0) {
            builder.expireAfterWrite(this.expireAfterWrite, this.expireTimeUnit);
        }
        if (this.expireAfterAccess > 0) {
            builder.expireAfterAccess(this.expireAfterAccess, this.expireTimeUnit);
        }
        if (this.refreshAfterWrite > 0) {
            builder.refreshAfterWrite(this.refreshAfterWrite, this.expireTimeUnit);
        }

        return builder.build();
    }
}