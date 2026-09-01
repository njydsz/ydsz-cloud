package com.njydsz.common.cache.spring;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cache.Cache.ValueRetrievalException;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CacheProtectionGuard;

/**
 * YdszCache 的 Spring Cache 适配器（Spring 6.x）。
 *
 * <p>将 YdszCache 的 Cache 接口适配为 Spring Cache 的标准接口， 支持 @Cacheable、@CachePut、@CacheEvict 等 Spring
 * Cache 注解。
 *
 * <p><b>注解级空值 TTL（防穿透，对标 Spring Cache null TTL 配置惯例）</b>： 配置 {@code
 * nullValueTtlMinMs/nullValueTtlMaxMs} 后，valueLoader 返回 null 时不再写入跟随主 TTL 的 NullValue
 * 条目，改为注册带随机抖动的短 TTL 空值占位—— 占位期内注解路径直接返回 null 不回源，过期后自动恢复加载，且短 TTL
 * 大幅缩小"后端恢复后仍被 null 屏蔽"的窗口。 未配置时保持旧行为（NullValue 包装 + 主 TTL，向后兼容）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SpringYdszCache extends AbstractValueAdaptingCache {

  private final String name;
  private final Cache<Object, Object> delegate;

  /** 单飞加载信号（防击穿）：key → 完成信号 Future，仅存储结果不承载额外状态 */
  private final ConcurrentMap<Object, CompletableFuture<Object>> pendingLoads =
      new ConcurrentHashMap<>();

  /**
   * 空值占位 TTL 下界（毫秒，0 表示禁用注解级空值短 TTL，走 NullValue 包装 + 主 TTL）。
   */
  private volatile long nullValueTtlMinMs = 0;

  /**
   * 空值占位 TTL 上界（毫秒，0 表示禁用）。
   */
  private volatile long nullValueTtlMaxMs = 0;

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

  /**
   * 配置注解路径的空值占位 TTL 区间（毫秒，带随机抖动防雪崩）。
   *
   * @param minMs 最小过期时间（毫秒）
   * @param maxMs 最大过期时间（毫秒）
   */
  public void setNullValueTtl(long minMs, long maxMs) {
    this.nullValueTtlMinMs = Math.max(0, minMs);
    this.nullValueTtlMaxMs = Math.max(this.nullValueTtlMinMs, maxMs);
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
    Object value = this.delegate.getIfPresent(key);
    if (value != null) {
      return value;
    }
    // 空值占位（防穿透，注解路径三防能力）：活动期内视为命中 null 值，不回源；
    // 已过期时占位被惰性清理，返回 null 触发正常加载
    if (this.nullValueTtlMaxMs > 0
        && CacheProtectionGuard.isNullPlaceholderActive(this.delegate, key)) {
      return isAllowNullValues() ? NullValue.INSTANCE : null;
    }
    return null;
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
          if (value != null) {
            result = toStoreValue(value);
            delegate.put(key, result);
          } else if (isAllowNullValues()) {
            if (nullValueTtlMaxMs > 0) {
              // 注解级空值短 TTL（防穿透 + 防雪崩抖动）：
              // 不写跟随主 TTL 的 NullValue 条目，注册短 TTL 占位，过期后自动恢复回源
              CacheProtectionGuard.registerNullPlaceholder(
                  delegate, key, nullValueTtlMinMs, nullValueTtlMaxMs);
              result = null;
            } else {
              // 未配置空值 TTL：保持旧行为（NullValue 包装 + 主 TTL）
              result = toStoreValue(value);
              delegate.put(key, result);
            }
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
