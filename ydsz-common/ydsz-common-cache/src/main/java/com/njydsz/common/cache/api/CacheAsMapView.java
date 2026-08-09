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
 * @author ydsz-team
 *
 * @since 1.0.0
 */
public class CacheAsMapView<K, V> implements Map<K, V> {

  private final Cache<K, V> cache;

  public CacheAsMapView(Cache<K, V> cache) {
    this.cache = cache;
  }

  /**
   * 返回缓存中键值对的估计数量。
   *
   * <p>与 {@link java.util.HashMap#size()} 不同，底层缓存 {@link Cache#estimatedSize()} 通常是
   * 近似值（并发缓存可能包含正在维护的过期条目），此处超出 {@link Integer#MAX_VALUE} 时按最大值截断。
   *
   * @return 缓存条目数（近似值，最大为 {@link Integer#MAX_VALUE}）
   */
  @Override
  public int size() {
    long size = cache.estimatedSize();
    return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
  }

  /**
   * 判断缓存是否为空。
   *
   * @return 缓存中无任何键值对时返回 {@code true}
   */
  @Override
  public boolean isEmpty() {
    return cache.isEmpty();
  }

  /**
   * 判断缓存中是否存在指定键。
   *
   * <p>与标准 {@link Map} 契约不同，null 键视为不存在，直接返回 {@code false} 而非抛异常。
   *
   * @param key 待检查的键，为 null 时返回 {@code false}
   * @return 缓存中存在该键时返回 {@code true}
   */
  @Override
    public boolean containsKey(Object key) {
    if (key == null) {
      return false;
    }
    return cache.containsKey((K) key);
  }

  /**
   * 判断缓存中是否存在指定值。
   *
   * <p>通过遍历底层缓存的键集合逐键比对，时间复杂度为 O(n)；null 值不参与匹配。
   *
   * @param value 待检查的值，为 null 时返回 {@code false}
   * @return 缓存中存在相等值（使用 {@link Object#equals}）时返回 {@code true}
   */
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

  /**
   * 获取指定键对应的缓存值。
   *
   * <p>与标准 {@link Map} 契约不同，null 键视为未命中，返回 {@code null} 而非抛异常。
   * 未命中或键为 null 时均返回 {@code null}，且不触发任何加载。
   *
   * @param key 待查询的键，为 null 时返回 {@code null}
   * @return 缓存值；键不存在时返回 {@code null}
   */
  @Override
    public V get(Object key) {
    if (key == null) {
      return null;
    }
    return cache.getIfPresent((K) key);
  }

  /**
   * 写入键值对并返回被替换的旧值。
   *
   * <p>null 键或 null 值被拒绝并抛出 {@link NullPointerException}，以维持视图的 null 契约
   * （与底层缓存允许 null 占位的语义不同）。返回旧值仅在键已存在时有意义。
   *
   * @param key   缓存键，不可为 {@code null}
   * @param value 缓存值，不可为 {@code null}
   * @return 被替换的旧值；键此前不存在时返回 {@code null}
   * @throws NullPointerException 当 key 或 value 为 null 时抛出
   */
  @Override
  public V put(K key, V value) {
    if (key == null || value == null) {
      throw new NullPointerException("key and value must not be null");
    }
    V oldValue = cache.getIfPresent(key);
    cache.put(key, value);
    return oldValue;
  }

  /**
   * 移除指定键并返回被移除的值。
   *
   * <p>null 键视为未命中，直接返回 {@code null} 而非抛异常；未命中同样返回 {@code null}。
   *
   * @param key 待移除的键，为 null 时返回 {@code null}
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
    public V remove(Object key) {
    if (key == null) {
      return null;
    }
    return cache.remove((K) key);
  }

  /**
   * 批量写入映射中的全部键值对。
   *
   * <p>逐条委托 {@link #put}，任一键值对含 null 都会抛出 {@link NullPointerException} 且
   * 后续条目不再写入（非原子）。空映射直接返回，不产生任何副作用。
   *
   * @param m 待写入的映射，可为空
   */
  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    if (m == null || m.isEmpty()) {
      return;
    }
    m.forEach(this::put);
  }

  /**
   * 清空缓存。
   *
   * <p>委托底层缓存的 {@link Cache#clear()}，移除全部键值对。
   */
  @Override
  public void clear() {
    cache.clear();
  }

  /**
   * 返回缓存键的视图集合。
   *
   * <p>直接透传底层缓存的 {@link Cache#keySet()}，其迭代器特性（弱一致、实时性）由底层实现决定。
   *
   * @return 缓存当前所有键的集合视图
   */
  @Override
  public Set<K> keySet() {
    return cache.keySet();
  }

  /**
   * 返回缓存值的视图集合。
   *
   * <p>直接透传底层缓存的 {@link Cache#values()}，迭代过程中并发修改的行为由底层实现决定。
   *
   * @return 缓存当前所有值的集合视图
   */
  @Override
  public Collection<V> values() {
    return cache.values();
  }

  /**
   * 返回缓存条目的视图集合。
   *
   * <p>返回的 entry 为不可变的 {@link SimpleImmutableEntry}，但该视图的 {@code remove} 操作
   * 支持删除缓存中的对应条目。视图基于底层 {@link Cache#keySet()} 迭代，属于弱一致的实时快照。
   *
   * @return 缓存当前所有条目的集合视图
   */
  @Override
  public Set<Entry<K, V>> entrySet() {
    return new AbstractSet<>() {
      /**
       * 返回遍历缓存条目的迭代器。
       *
       * <p>迭代基于底层缓存的键集合，每次 {@link #next()} 都实时读取对应值；
       * 若迭代过程中键被删除，则该键对应的 entry 值为 null。
       *
       * @return 遍历当前缓存条目的迭代器
       */
      @Override
      public Iterator<Entry<K, V>> iterator() {
        return new Iterator<>() {
          private final Iterator<K> keyIterator = cache.keySet().iterator();
          private K currentKey;

          /**
           * 判断迭代器是否还有下一个条目。
           *
           * @return 底层键集合迭代器有剩余元素时返回 {@code true}
           */
          @Override
          public boolean hasNext() {
            return keyIterator.hasNext();
          }

          /**
           * 返回下一个缓存条目。
           *
           * <p>以当前键实时查询缓存值构造条目，若键在迭代间隙被移除，值可能为 null。
           *
           * @return 下一个条目，键值对为不可变快照
           */
          @Override
          public Entry<K, V> next() {
            currentKey = keyIterator.next();
            V value = cache.getIfPresent(currentKey);
            return new SimpleImmutableEntry<>(currentKey, value);
          }
        };
      }

      /**
       * 返回缓存条目数（近似值）。
       *
       * <p>委托外部视图的 {@link CacheAsMapView#size()}，同样为近似语义。
       *
       * @return 当前缓存条目数
       */
      @Override
      public int size() {
        return CacheAsMapView.this.size();
      }

      /**
       * 从缓存中移除与指定条目键值都匹配的条目。
       *
       * <p>仅当缓存中对应键的值与条目携带的值相等（非 null）时才执行删除， 否则不做任何修改。
       *
       * @param o 待匹配的 {@link Entry} 对象；非 Entry 或键不存在时返回 {@code false}
       * @return 成功删除匹配条目时返回 {@code true}
       */
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
