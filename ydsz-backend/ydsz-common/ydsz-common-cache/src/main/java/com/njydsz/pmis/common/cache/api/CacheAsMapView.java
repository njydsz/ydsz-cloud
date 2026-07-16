package com.njydsz.common.cache.api;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 缓存的 Map 视图实现
 *
 * <p>该 Map 视图是缓存的实时视图，对该 Map 的修改会反映到缓存中。 注意：该 Map 不支持 null 键或 null 值。
 *
 * <p>从 {@link Cache} 接口中提取，遵循单一职责原则。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * 
 */
public class CacheAsMapView<K, V> implements Map<K, V> {

  private final Cache<K, V> cache;

  public CacheAsMapView(Cache<K, V> cache) {
    this.cache = cache;
  }

  @Override
  public int size() {
    long size = cache.estimatedSize();
    return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
  }

  @Override
  public boolean isEmpty() {
    return cache.isEmpty();
  }

  @Override
    public boolean containsKey(Object key) {
    if (key == null) {
      return false;
    }
    return cache.containsKey((K) key);
  }

  @Override
  public boolean containsValue(Object value) {
    if (value == null) {
      return false;
    }
    for (K key : cache.keySet()) {
      V v = cache.getIfPresent(key);
      if (value.equals(v)) {
        return true;
      }
    }
    return false;
  }

  @Override
    public V get(Object key) {
    if (key == null) {
      return null;
    }
    return cache.getIfPresent((K) key);
  }

  @Override
  public V put(K key, V value) {
    if (key == null || value == null) {
      throw new NullPointerException("key and value must not be null");
    }
    V oldValue = cache.getIfPresent(key);
    cache.put(key, value);
    return oldValue;
  }

  @Override
    public V remove(Object key) {
    if (key == null) {
      return null;
    }
    return cache.remove((K) key);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    if (m == null || m.isEmpty()) {
      return;
    }
    m.forEach(this::put);
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public Set<K> keySet() {
    return cache.keySet();
  }

  @Override
  public Collection<V> values() {
    return cache.values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new AbstractSet<>() {
      @Override
      public Iterator<Entry<K, V>> iterator() {
        return new Iterator<>() {
          private final Iterator<K> keyIterator = cache.keySet().iterator();
          private K currentKey;

          @Override
          public boolean hasNext() {
            return keyIterator.hasNext();
          }

          @Override
          public Entry<K, V> next() {
            currentKey = keyIterator.next();
            V value = cache.getIfPresent(currentKey);
            return new SimpleImmutableEntry<>(currentKey, value);
          }
        };
      }

      @Override
      public int size() {
        return CacheAsMapView.this.size();
      }

      @Override
            public boolean remove(Object o) {
        if (!(o instanceof Entry)) {
          return false;
        }
        Entry<?, ?> entry = (Entry<?, ?>) o;
        Object entryKey = entry.getKey();
        V value = cache.getIfPresent((K) entryKey);
        if (value != null && value.equals(entry.getValue())) {
          cache.remove((K) entryKey);
          return true;
        }
        return false;
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Map)) {
      return false;
    }
    Map<?, ?> m = (Map<?, ?>) o;
    if (this.size() != m.size()) {
      return false;
    }
    try {
      for (K key : cache.keySet()) {
        V v = cache.getIfPresent(key);
        Object mv = m.get(key);
        if (v == null) {
          if (mv != null || !m.containsKey(key)) {
            return false;
          }
        } else if (!v.equals(mv)) {
          return false;
        }
      }
    } catch (ClassCastException | NullPointerException unused) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int h = 0;
    for (K key : cache.keySet()) {
      V v = cache.getIfPresent(key);
      h += (key == null ? 0 : key.hashCode()) ^ (v == null ? 0 : v.hashCode());
    }
    return h;
  }

  @Override
  public String toString() {
    if (cache.isEmpty()) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    boolean first = true;
    for (K key : cache.keySet()) {
      if (!first) {
        sb.append(", ");
      }
      V value = cache.getIfPresent(key);
      sb.append(key == this ? "(this Map)" : key);
      sb.append('=');
      sb.append(value == this ? "(this Map)" : value);
      first = false;
    }
    sb.append('}');
    return sb.toString();
  }
}
