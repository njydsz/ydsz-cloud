package com.njydsz.common.safe.cache;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.builder.CacheType;

/**
 * 将 ydzs-common-cache Cache 适配为 SafeCache 的桥接实现。
 *
 * <p>此类仅在 ydzs-common-cache 在 classpath 时被加载，由调用方在确认 cache 可用后使用，不引入编译期强制依赖。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 26.09.01
 */
public final class YdszCacheBridge<K, V> implements SafeCache<K, V> {

  private static final Logger LOG = LoggerFactory.getLogger(YdszCacheBridge.class);

  private final Cache<K, V> delegate;

  public YdszCacheBridge(Cache<K, V> delegate) {
    this.delegate = delegate;
  }

  /**
   * 使用 YdszCache Builder 构建指定配置的 SafeCache
   *
   * @param expireAfterWrite 写入后过期时间
   * @param timeUnit 时间单位
   * @param maxSize 最大条目数（0 表示不限制）
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 基于 ydzs-common-cache 的 SafeCache 实例
   * @throws NoClassDefFoundError 当 ydzs-common-cache 不在 classpath 时抛出
   */
  public static <K, V> SafeCache<K, V> create(long expireAfterWrite, TimeUnit timeUnit, long maxSize) {
    CacheBuilder<K, V> builder =
        YdszCache.newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(expireAfterWrite, timeUnit);
    if (maxSize > 0) {
      builder.maximumSize(maxSize);
    }
    Cache<K, V> cache = builder.build();

    LOG.debug("创建 YdszCacheBridge: ttl={}, unit={}, maxSize={}",
        expireAfterWrite, timeUnit.name(), maxSize > 0 ? maxSize : "unlimited");

    return new YdszCacheBridge<>(cache);
  }

  @Override
  public V getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    return delegate.putIfAbsent(key, value);
  }

  @Override
  public void invalidate(K key) {
    delegate.invalidate(key);
  }

  @Override
  public void invalidateAll() {
    delegate.invalidateAll();
  }

  @Override
  public long estimatedSize() {
    return delegate.estimatedSize();
  }

  @Override
  public void cleanUp() {
    delegate.cleanUp();
  }
}
