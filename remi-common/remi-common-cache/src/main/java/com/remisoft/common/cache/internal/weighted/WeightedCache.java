package com.remisoft.common.cache.internal.weighted;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.cache.internal.AbstractCache;
import com.remisoft.common.cache.listener.RemovalCause;
import com.remisoft.common.cache.support.Weigher;

/**
 * 权重缓存实现 - 基于权重的淘汰策略
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>权重淘汰：根据元素权重进行淘汰，权重高的元素可能被淘汰
 *   <li>可配置权重：支持自定义权重计算器，灵活控制权重策略
 *   <li>容量保护：总权重不超过上限，防止内存溢出
 *   <li>线程安全：使用 ConcurrentHashMap 保证并发安全
 * </ul>
 *
 * <p>工作原理：
 *
 * <ol>
 *   <li>每个缓存项关联一个权重值
 *   <li>put 操作时检查总权重是否超限
 *   <li>超限时按插入顺序淘汰旧元素直到权重满足
 *   <li>权重计算器由用户自定义（Weigher 接口）
 * </ol>
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>内存敏感场景：不同大小对象需要不同的缓存配额
 *   <li>资源受限环境：如嵌入式系统、移动设备
 *   <li>多租户场景：按租户分配缓存配额
 * </ul>
 *
 * <p>权重计算示例：
 *
 * <pre>{@code
 * // 按字符串长度作为权重
 * Weigher<String, String> weigher = (key, value) -> value.length();
 * Cache<String, String> cache = RemiCache.createWeightedCache(10000, weigher);
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author remi-team
 * @since 1.0.0
 * 
 */
public class WeightedCache<K, V> extends AbstractCache<K, V> {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(WeightedCache.class);

  /** 底层并发存储映射 */
  private final ConcurrentMap<K, V> map;

  /** 权重计算器 */
  private final Weigher<? super K, ? super V> weigher;

  /** 最大权重限制 */
  private final long maxWeight;

  /** 当前总权重 */
  private final LongAdder currentWeight = new LongAdder();

  public WeightedCache(long maxWeight, Weigher<? super K, ? super V> weigher) {
    this(maxWeight, 16, weigher);
  }

  public WeightedCache(long maxWeight, int initialCapacity, Weigher<? super K, ? super V> weigher) {
    this.maxWeight = maxWeight;
    this.weigher = weigher;
    this.map = new ConcurrentHashMap<>(initialCapacity);
  }

  /**
   * 获取指定 key 的缓存值，不做加载。
   *
   * <p>命中/未命中均计入统计；未命中返回 {@code null}，不触发加载。
   *
   * @param key 查询的键，不可为 {@code null}
   * @return 已缓存的值；未命中返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    V value = map.get(key);
    if (value != null) {
      hitCount.increment();
    } else {
      missCount.increment();
    }
    return value;
  }

  /**
   * 写入缓存并维护总权重，超限时按插入顺序淘汰旧条目。
   *
   * <p>写操作在对象级锁内完成以保证权重计数一致；若替换已有 key 会扣减旧值权重
   * 并通知 REPLACED 删除事件。总权重超过 {@code maxWeight} 且条目数大于 1 时，
   * 循环淘汰最早插入的条目直到满足容量约束。
   *
   * @param key   写入的键，不可为 {@code null}
   * @param value 写入的值，权重由 {@link Weigher#weigh} 计算
   */
  @Override
  public void put(K key, V value) {
    long weight = weigher.weigh(key, value);

    synchronized (this) {
      V oldValue = map.put(key, value);
      if (oldValue != null) {
        long oldWeight = weigher.weigh(key, oldValue);
        currentWeight.add(weight - oldWeight);
        notifyRemoval(key, oldValue, RemovalCause.REPLACED);
      } else {
        currentWeight.add(weight);
      }

      while (currentWeight.sum() > maxWeight && map.size() > 1) {
        evict();
      }
    }
  }

  /**
   * 移除指定 key 并扣减总权重。
   *
   * <p>仅当旧值非空时扣减权重并通知 EXPLICIT 删除事件。
   *
   * @param key 待移除的键，不可为 {@code null}
   * @return 被移除的旧值；key 不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    synchronized (this) {
      V value = map.remove(key);
      if (value != null) {
        currentWeight.add(-weigher.weigh(key, value));
        notifyRemoval(key, value, RemovalCause.EXPLICIT);
      }
      return value;
    }
  }

  /**
   * 清空全部缓存条目并重置总权重。
   *
   * <p>对每个被清空的条目逐一触发 EXPLICIT 删除事件。
   */
  @Override
  public void clear() {
    map.forEach((key, value) -> notifyRemoval(key, value, RemovalCause.EXPLICIT));
    map.clear();
    currentWeight.reset();
  }

  /**
   * 返回当前缓存中的条目数。
   *
   * @return 已缓存条目的数量
   */
  @Override
  public long estimatedSize() {
    return map.size();
  }

  /**
   * 判断指定 key 是否已缓存。
   *
   * @param key 查询的键，不可为 {@code null}
   * @return true 表示该 key 存在于缓存中
   */
  @Override
  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  /**
   * 返回当前缓存键的快照视图。
   *
   * @return 缓存键的 {@link Set}
   */
  @Override
  public Set<K> keySet() {
    return map.keySet();
  }

  /**
   * 返回当前缓存值的集合视图。
   *
   * @return 缓存值的 {@link Collection}
   */
  @Override
  public Collection<V> values() {
    return map.values();
  }

  private void evict() {
    Map.Entry<K, V> eldest = null;
    for (Map.Entry<K, V> entry : map.entrySet()) {
      eldest = entry;
      break;
    }

    if (eldest != null) {
      K key = eldest.getKey();
      V value = map.remove(key);
      if (value != null) {
        long weight = weigher.weigh(key, value);
        currentWeight.add(-weight);
        log.debug("权重缓存淘汰，key={}, weight={}", key, weight);
        notifyRemoval(key, value, RemovalCause.SIZE);
      }
    }
  }

  /**
   * 获取当前缓存总权重
   *
   * @return 当前总权重
   */
  public long getCurrentWeight() {
    return currentWeight.sum();
  }

  /**
   * 获取缓存最大权重限制
   *
   * @return 最大权重
   */
  public long getMaxWeight() {
    return maxWeight;
  }
}
