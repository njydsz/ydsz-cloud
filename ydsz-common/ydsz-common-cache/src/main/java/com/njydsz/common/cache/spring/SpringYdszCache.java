package com.njydsz.common.cache.spring;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cache.Cache.ValueRetrievalException;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import com.njydsz.common.cache.api.Cache;

/**
 * YdszCache 的 Spring Cache 适配器（Spring 6.x）。
 *
 * <p>将 YdszCache 的 Cache 接口适配为 Spring Cache 的标准接口，
 * 支持 @Cacheable、@CachePut、@CacheEvict 等 Spring Cache 注解。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class SpringYdszCache extends AbstractValueAdaptingCache {

  private final String name;
  private final Cache<Object, Object> delegate;

  /** 单飞加载信号（防击穿）：key → 完成信号 Future，仅存储结果不承载额外状态 */
  private final ConcurrentMap<Object, CompletableFuture<Object>> pendingLoads =
      new ConcurrentHashMap<>();

  /**
   * 创建 Spring YdszCache 适配器。
   *
   * @param name 缓存名称
   * @param delegate YdszCache 实例
   * @param allowNullValues 是否允许 null 值
   */
  public SpringYdszCache(String name, Cache<Object, Object> delegate, boolean allowNullValues) {
    super(allowNullValues);
    this.name = name;
    this.delegate = delegate;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public Cache<Object, Object> getNativeCache() {
    return this.delegate;
  }

  @Override
  protected Object lookup(Object key) {
    return this.delegate.getIfPresent(key);
  }

  @Override
  public <T> T get(Object key, Class<T> type) {
    if (type == null) {
      return super.get(key, type);
    }
    Object value = fromStoreValue(lookup(key));
    if (value != null && !type.isInstance(value)) {
      throw new IllegalStateException(
          "Cached value is not of required type [" + type.getName() + "]: " + value);
    }
    return type.cast(value);
  }

  @Override
  public <T> T get(Object key, Callable<T> valueLoader) {
    Object storeValue = lookup(key);
    if (storeValue != null) {
      return (T) fromStoreValue(storeValue);
    }

    // 单飞防击穿：同一 key 的并发请求仅一个执行 valueLoader，其余等待结果
    CompletableFuture<Object> ourFuture = new CompletableFuture<>();
    CompletableFuture<Object> existing = pendingLoads.putIfAbsent(key, ourFuture);
    if (existing == null) {
      try {
        Object result;
        try {
          T value = valueLoader.call();
          if (value != null || isAllowNullValues()) {
            result = toStoreValue(value);
            delegate.put(key, result);
          } else {
            result = null;
          }
        } catch (Exception e) {
          ourFuture.completeExceptionally(e);
          throw new ValueRetrievalException(key, valueLoader, e);
        }
        ourFuture.complete(result);
        return result == null ? null : (T) fromStoreValue(result);
      } finally {
        pendingLoads.remove(key, ourFuture);
      }
    }

    // 等待加载线程完成
    try {
      existing.join();
    } catch (CompletionException e) {
      Throwable cause = e.getCause();
      throw new ValueRetrievalException(key, valueLoader, cause != null ? cause : e);
    }
    Object awaited = lookup(key);
    if (awaited != null) {
      return (T) fromStoreValue(awaited);
    }
    return null;
  }

  @Override
  public void put(Object key, Object value) {
    if (!isAllowNullValues() && value == null) {
      return;
    }
    Object storeValue = toStoreValue(value);
    this.delegate.put(key, storeValue);
  }

  @Override
  public ValueWrapper putIfAbsent(Object key, Object value) {
    if (!isAllowNullValues() && value == null) {
      return null;
    }
    Object storeValue = toStoreValue(value);
    Object existing = this.delegate.putIfAbsent(key, storeValue);
    return toValueWrapper(existing);
  }

  @Override
  public void evict(Object key) {
    this.delegate.invalidate(key);
  }

  @Override
  public boolean evictIfPresent(Object key) {
    if (key != null && this.delegate.getIfPresent(key) != null) {
      this.delegate.invalidate(key);
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    this.delegate.invalidateAll();
  }

  @Override
  public boolean invalidate() {
    clear();
    return true;
  }
}
