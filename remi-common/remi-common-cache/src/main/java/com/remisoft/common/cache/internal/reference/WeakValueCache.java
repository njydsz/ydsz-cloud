package com.remisoft.common.cache.internal.reference;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.cache.internal.AbstractCache;
import com.remisoft.common.cache.listener.RemovalCause;

/**
 * 弱引用值缓存实现 - 使用 WeakReference 作为值的缓存
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>弱引用值：当值不再被其他引用时，可被 GC 回收
 *   <li>键保留：键对象正常保留，不影响查找
 *   <li>自动清理：GC 时自动清理被回收的值
 *   <li>线程安全：使用 ConcurrentHashMap 保证并发安全
 * </ul>
 *
 * <p>工作原理：
 *
 * <ol>
 *   <li>使用 WeakReference 封装值对象
 *   <li>ReferenceQueue 用于跟踪被 GC 回收的值
 *   <li>每次缓存操作前先清理已回收的条目
 *   <li>当值对象只有缓存引用时，GC 会回收并回调
 * </ol>
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>缓存大对象：避免大对象占用过多内存
 *   <li>计算结果缓存：临时存储计算结果
 *   <li>资源池：管理稀缺资源
 * </ul>
 *
 * <p>注意事项：
 *
 * <ul>
 *   <li>值可能被 GC 回收，返回 null
 *   <li>不适合需要可靠返回值的场景
 *   <li>清理操作有轻微性能开销
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型（建议大对象）
 * @author remi-team
 * @since 1.0.0
 */
public class WeakValueCache<K, V> extends AbstractCache<K, V> {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(WeakValueCache.class);

  /** 底层并发存储映射 */
  private final ConcurrentMap<K, WeakReference<V>> map;

  /** 弱引用队列，用于跟踪被 GC 回收的值 */
  private final ReferenceQueue<V> queue;

  /** 清理时间间隔阈值（纳秒） */
  private static final long CLEANUP_INTERVAL_NANOS = 1_000_000L;

  /** 上次清理时间 */
  private volatile long lastCleanupTime = 0;

  public WeakValueCache() {
    this.map = new ConcurrentHashMap<>();
    this.queue = new ReferenceQueue<>();
  }

  private void maybeCleanup() {
    long now = System.nanoTime();
    if (now - lastCleanupTime > CLEANUP_INTERVAL_NANOS) {
      lastCleanupTime = now;
      cleanup();
    }
  }

  private void cleanup() {
    int[] removed = {0};
    while (queue.poll() != null) {
      removed[0]++;
    }
    map.entrySet().removeIf(entry -> entry.getValue().get() == null);
    if (removed[0] > 0) {
      log.debug("WeakValueCache GC 清理完成，移除引用数={}", removed[0]);
    }
  }

  /**
   * 获取缓存值（不触发加载）。
   *
   * <p>值被 GC 回收后等价于未命中：返回 null 并计入 miss。访问前触发节流式惰性清理。
   *
   * @param key 缓存键
   * @return 缓存值；未命中或弱引用已被 GC 回收时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    maybeCleanup();
    WeakReference<V> ref = map.get(key);
    V value = ref != null ? ref.get() : null;
    if (value != null) {
      hitCount.increment();
    } else {
      missCount.increment();
    }
    return value;
  }

  /**
   * 写入键值对，值以弱引用形式存储。
   *
   * <p>值对象不再被外部强引用时，GC 即可回收（此时读路径返回 null）； 回收事件通过引用队列跟踪，用于清理残留条目。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    maybeCleanup();
    WeakReference<V> ref = new WeakReference<>(value, queue);
    map.put(key, ref);
  }

  /**
   * 移除指定键并返回被移除的值。
   *
   * <p>值若已被 GC 回收，则无法返回原值，仅清理条目（不发送删除通知）。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在或值已被回收时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    maybeCleanup();
    WeakReference<V> ref = map.remove(key);
    V value = ref != null ? ref.get() : null;
    if (value != null) {
      notifyRemoval(key, value, RemovalCause.EXPLICIT);
    }
    return value;
  }

  /**
   * 清空缓存。
   *
   * <p>对值仍存活（未被回收）的条目发送 {@link RemovalCause#EXPLICIT} 通知， 同时清空底层映射与引用队列。
   *
   */
  @Override
  public void clear() {
    map.forEach(
        (key, ref) -> {
          V value = ref.get();
          if (value != null) {
            notifyRemoval(key, value, RemovalCause.EXPLICIT);
          }
        });
    map.clear();
    // 清空引用队列（防 GC 回调残留）
    while (queue.poll() != null) {
        // drain reference queue
    }
  }

  /**
   * 返回值仍存活（未被 GC 回收）的缓存条目数。
   *
   * @return 存活条目数
   */
  @Override
  public long estimatedSize() {
    maybeCleanup();
    return map.entrySet().stream().filter(e -> e.getValue().get() != null).count();
  }

  /**
   * 判断缓存中是否存在指定键（值未被 GC 回收）。
   *
   * @param key 缓存键
   * @return 键存在且值存活时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    maybeCleanup();
    WeakReference<V> ref = map.get(key);
    return ref != null && ref.get() != null;
  }

  /**
   * 返回缓存键集合（透传底层并发映射，弱一致，可能含残留键）。
   *
   * @return 缓存键集合视图
   */
  @Override
  public Set<K> keySet() {
    maybeCleanup();
    return map.keySet();
  }

  /**
   * 返回缓存值集合。
   *
   * <p>复制为一次性快照，仅包含仍存活的值；已回收值的条目被过滤。
   *
   * @return 当前存活值的快照集合
   */
  @Override
  public Collection<V> values() {
    maybeCleanup();
    List<V> list = new ArrayList<>();
    for (WeakReference<V> ref : map.values()) {
      V value = ref.get();
      if (value != null) {
        list.add(value);
      }
    }
    return list;
  }
}
