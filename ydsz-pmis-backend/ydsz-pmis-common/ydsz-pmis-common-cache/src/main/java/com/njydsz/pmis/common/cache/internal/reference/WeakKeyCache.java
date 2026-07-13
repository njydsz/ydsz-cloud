package com.njydsz.pmis.common.cache.internal.reference;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.internal.AbstractCache;
import com.njydsz.pmis.common.cache.listener.RemovalCause;

public class WeakKeyCache<K, V> extends AbstractCache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(WeakKeyCache.class);

  private final ConcurrentMap<WeakReferenceKey<K>, V> map;

  private final ReferenceQueue<K> queue;

  private static final long CLEANUP_INTERVAL_NANOS = 1_000_000L;

  private volatile long lastCleanupTime = 0;

  private final boolean recordStats;

  public WeakKeyCache() {
    this(true);
  }

  public WeakKeyCache(boolean recordStats) {
    this.map = new ConcurrentHashMap<>();
    this.queue = new ReferenceQueue<>();
    this.recordStats = recordStats;
  }

  private WeakReferenceKey<K> lookupKey(K key) {
    return new WeakReferenceKey<>(key, null);
  }

  private void maybeCleanup() {
    long now = System.nanoTime();
    if (now - lastCleanupTime > CLEANUP_INTERVAL_NANOS) {
      lastCleanupTime = now;
      cleanup();
    }
  }

  private void cleanup() {
    WeakReferenceKey<? extends K> ref;
    int[] removed = {0};
    while ((ref = (WeakReferenceKey<? extends K>) queue.poll()) != null) {
      V value = map.remove(ref);
      if (value != null) {
        K key = ref.getKey();
        notifyRemoval(key, value, RemovalCause.COLLECTED);
        removed[0]++;
      }
    }
    if (removed[0] > 0) {
      log.debug("WeakKeyCache GC 清理完成，移除条目数={}", removed[0]);
    }
  }

  @Override
  public V getIfPresent(K key) {
    maybeCleanup();
    V value = map.get(lookupKey(key));
    if (recordStats) {
      if (value != null) {
        hitCount.increment();
      } else {
        missCount.increment();
      }
    }
    return value;
  }

  @Override
  public void put(K key, V value) {
    maybeCleanup();
    map.remove(lookupKey(key));
    map.put(new WeakReferenceKey<>(key, queue), value);
  }

  @Override
  public V remove(K key) {
    maybeCleanup();
    V value = map.remove(lookupKey(key));
    if (value != null) {
      notifyRemoval(key, value, RemovalCause.EXPLICIT);
    }
    return value;
  }

  @Override
  public void clear() {
    map.forEach(
        (ref, value) -> {
          K key = ref.getKey();
          notifyRemoval(key, value, RemovalCause.EXPLICIT);
        });
    map.clear();
    while (queue.poll() != null) {}
  }

  @Override
  public long estimatedSize() {
    maybeCleanup();
    return map.size();
  }

  @Override
  public boolean containsKey(K key) {
    maybeCleanup();
    return map.containsKey(lookupKey(key));
  }

  @Override
  public Set<K> keySet() {
    maybeCleanup();
    Set<K> keys = new HashSet<>();
    for (WeakReferenceKey<K> ref : map.keySet()) {
      K key = ref.getKey();
      if (key != null) {
        keys.add(key);
      }
    }
    return keys;
  }

  @Override
  public Collection<V> values() {
    maybeCleanup();
    return map.values();
  }

  private static class WeakReferenceKey<K> extends WeakReference<K> {
    private final int hashCode;

    WeakReferenceKey(K key, ReferenceQueue<? super K> queue) {
      super(key, queue);
      this.hashCode = System.identityHashCode(key);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof WeakReferenceKey)) {
        return false;
      }
      WeakReferenceKey<?> other = (WeakReferenceKey<?>) obj;
      if (hashCode != other.hashCode) {
        return false;
      }
      K thisKey = get();
      Object otherKey = other.get();
      if (thisKey == null || otherKey == null) {
        return false;
      }
      return thisKey == otherKey;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    public K getKey() {
      return get();
    }
  }
}
