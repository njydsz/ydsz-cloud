package com.njydsz.pmis.common.cache.spring;

import java.util.concurrent.Callable;

import org.springframework.cache.Cache.ValueRetrievalException;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import com.njydsz.pmis.common.cache.api.Cache;

/**
 * YdszCache 的 Spring Cache 适配器（Spring 6.x）
 *
 * <p>将 YdszCache 的 Cache 接口适配为 Spring Cache 的标准接口， 使 YdszCache 支持 @Cacheable、@CachePut、@CacheEvict
 * 等 Spring Cache 注解。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@SuppressWarnings("unchecked")
public class SpringYdszCache extends AbstractValueAdaptingCache {

  private final String name;
  private final Cache<Object, Object> delegate;

  /**
   * 创建 Spring YdszCache 适配器
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
    try {
      T newValue = valueLoader.call();
      put(key, newValue);
      return newValue;
    } catch (Exception e) {
      throw new ValueRetrievalException(key, valueLoader, e);
    }
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
