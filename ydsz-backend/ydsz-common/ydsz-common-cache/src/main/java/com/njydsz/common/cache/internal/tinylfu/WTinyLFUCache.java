package com.njydsz.common.cache.internal.tinylfu;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.internal.lfu.FrequencySketch;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;

/**
 * @deprecated 使用 {@link WindowTinyLFUCache} 替代。WindowTinyLFUCache 提供了分段锁架构和无锁读取，
 *     性能和并发性更优。通过 {@code YdszCache.newBuilder().type(CacheType.TINYLFU).build()} 默认使用 WindowTinyLFUCache。
 */
@Deprecated
public class WTinyLFUCache<K, V> extends AbstractCache<K, V> {

  private final int maximumSize;
  private final ConcurrentHashMap<K, CacheEntry<V>> cache;
  private final FrequencySketch frequencySketch;
  private final AtomicLong evictionCount = new AtomicLong();
  private final long defaultTtlMillis;

  public WTinyLFUCache(int maximumSize) {
    this(maximumSize, 0);
  }

  public WTinyLFUCache(int maximumSize, long defaultTtlMillis) {
    this.maximumSize = maximumSize;
    this.defaultTtlMillis = defaultTtlMillis;
    this.cache = new ConcurrentHashMap<>(maximumSize);
    this.frequencySketch = new FrequencySketch();
    this.frequencySketch.ensureCapacity(maximumSize);
  }

  @Override
  public V getIfPresent(K key) {
    CacheEntry<V> entry = cache.get(key);
    if (entry == null) {
      missCount.increment();
      return null;
    }
    if (entry.isExpired()) {
      // 使用 remove 避免 TOCTOU：只有当 entry 仍然是同一个对象时才移除
      if (cache.remove(key, entry)) {
        evictionCount.incrementAndGet();
        notifyRemoval(key, entry.getValue(), RemovalCause.EXPIRED);
      }
      missCount.increment();
      return null;
    }
    frequencySketch.increment(key);
    hitCount.increment();
    return entry.getValue();
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value != null) {
      return value;
    }
    if (loader == null) {
      return null;
    }
    value = loader.apply(key);
    if (value != null) {
      put(key, value);
    }
    return value;
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return CompletableFuture.supplyAsync(
        () ->
            get(
                key,
                k -> {
                  try {
                    return loader.apply(k).join();
                  } catch (Exception e) {
                    return null;
                  }
                }));
  }

  @Override
  public boolean containsKey(K key) {
    CacheEntry<V> entry = cache.get(key);
    if (entry == null) {
      return false;
    }
    if (entry.isExpired()) {
      if (cache.remove(key, entry)) {
        evictionCount.incrementAndGet();
        notifyRemoval(key, entry.getValue(), RemovalCause.EXPIRED);
      }
      return false;
    }
    return true;
  }

  @Override
  public void put(K key, V value) {
    put(key, value, defaultTtlMillis);
  }

  public void put(K key, V value, long ttlMillis) {
    if (key == null || value == null) {
      return;
    }
    frequencySketch.increment(key);
    CacheEntry<V> newEntry = new CacheEntry<>(value, ttlMillis);
    CacheEntry<V> oldEntry = cache.put(key, newEntry);
    if (oldEntry != null) {
      notifyRemoval(key, oldEntry.getValue(), RemovalCause.REPLACED);
    } else if (cache.size() > maximumSize) {
      evict();
    }
  }

  @Override
  public V putIfAbsent(K key, V value) {
    CacheEntry<V> existing = cache.get(key);
    if (existing != null && !existing.isExpired()) {
      return existing.getValue();
    }
    put(key, value);
    return null;
  }

  @Override
  public V computeIfAbsent(K key, Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value != null) {
      return value;
    }
    return cache.computeIfAbsent(
                key,
                k -> {
                  V v = loader.apply(k);
                  if (v == null) {
                    // loader 返回 null 时不创建 CacheEntry，返回 null 标记
                    return null;
                  }
                  frequencySketch.increment(k);
                  return new CacheEntry<>(v, defaultTtlMillis);
                })
            != null
        ? cache.get(key).getValue()
        : null;
  }

  @Override
  public V remove(K key) {
    CacheEntry<V> entry = cache.remove(key);
    if (entry != null) {
      notifyRemoval(key, entry.getValue(), RemovalCause.EXPLICIT);
      return entry.getValue();
    }
    return null;
  }

  @Override
  public void invalidate(K key) {
    remove(key);
  }

  @Override
  public void invalidateAll() {
    clear();
  }

  @Override
  public void clear() {
    for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
      notifyRemoval(entry.getKey(), entry.getValue().getValue(), RemovalCause.EXPLICIT);
    }
    cache.clear();
  }

  @Override
  public long estimatedSize() {
    return cache.size();
  }

  @Override
  public void cleanUp() {
    Iterator<Map.Entry<K, CacheEntry<V>>> it = cache.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<K, CacheEntry<V>> entry = it.next();
      if (entry.getValue().isExpired()) {
        it.remove();
        evictionCount.incrementAndGet();
        notifyRemoval(entry.getKey(), entry.getValue().getValue(), RemovalCause.EXPIRED);
      }
    }
  }

  @Override
  public CacheStats getStats() {
    return new CacheStats(hitCount.sum(), missCount.sum(), evictionCount.get(), 0, 0, 0, 0);
  }

  @Override
  public double getHitRate() {
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0 : (double) hitCount.sum() / total;
  }

  @Override
  public Set<K> keySet() {
    return cache.keySet();
  }

  @Override
    public Collection<V> values() {
    return cache.values().stream().filter(e -> !e.isExpired()).map(CacheEntry::getValue).toList();
  }

  @Override
  public Map<K, V> asMap() {
    Map<K, V> map = new HashMap<>();
    for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
      if (!entry.getValue().isExpired()) {
        map.put(entry.getKey(), entry.getValue().getValue());
      }
    }
    return map;
  }

  private void evict() {
    K victimKey = null;
    int victimFreq = Integer.MAX_VALUE;
    for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
      if (entry.getValue().isExpired()) {
        cache.remove(entry.getKey());
        evictionCount.incrementAndGet();
        notifyRemoval(entry.getKey(), entry.getValue().getValue(), RemovalCause.EXPIRED);
        return;
      }
      int freq = frequencySketch.frequency(entry.getKey());
      if (freq < victimFreq) {
        victimFreq = freq;
        victimKey = entry.getKey();
      }
    }
    if (victimKey != null) {
      CacheEntry<V> removed = cache.remove(victimKey);
      if (removed != null) {
        evictionCount.incrementAndGet();
        notifyRemoval(victimKey, removed.getValue(), RemovalCause.SIZE);
      }
    }
  }

  private static class CacheEntry<V> {
    private final V value;
    private final long expireTime;

    CacheEntry(V value, long ttlMillis) {
      this.value = value;
      this.expireTime = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
    }

    V getValue() {
      return value;
    }

    boolean isExpired() {
      return System.currentTimeMillis() > expireTime;
    }
  }
}
