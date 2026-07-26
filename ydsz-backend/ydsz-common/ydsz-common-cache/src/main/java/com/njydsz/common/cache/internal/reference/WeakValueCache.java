package com.njydsz.common.cache.internal.reference;

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

import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.listener.RemovalCause;

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
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
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

  @Override
  public void put(K key, V value) {
    maybeCleanup();
    WeakReference<V> ref = new WeakReference<>(value, queue);
    map.put(key, ref);
  }

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
    while (queue.poll() != null) {}
  }

  @Override
  public long estimatedSize() {
    maybeCleanup();
    return map.entrySet().stream().filter(e -> e.getValue().get() != null).count();
  }

  @Override
  public boolean containsKey(K key) {
    maybeCleanup();
    WeakReference<V> ref = map.get(key);
    return ref != null && ref.get() != null;
  }

  @Override
  public Set<K> keySet() {
    maybeCleanup();
    return map.keySet();
  }

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
