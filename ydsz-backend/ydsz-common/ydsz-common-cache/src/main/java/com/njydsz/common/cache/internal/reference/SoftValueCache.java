package com.njydsz.common.cache.internal.reference;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
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
 * 软引用值缓存实现 - 使用 SoftReference 作为值的缓存
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>软引用值：当内存不足时，GC 会回收软引用对象
 *   <li>内存敏感：JVM 内存不足时自动释放缓存
 *   <li>优先保留：相比弱引用，软引用会被更晚回收
 *   <li>线程安全：使用 ConcurrentHashMap 保证并发安全
 * </ul>
 *
 * <p>工作原理：
 *
 * <ol>
 *   <li>使用 SoftReference 封装值对象
 *   <li>ReferenceQueue 用于跟踪被 GC 回收的值
 *   <li>每次缓存操作前先清理已回收的条目
 *   <li>当 JVM 内存不足时，GC 会根据 LRU 策略回收软引用
 * </ol>
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>内存敏感缓存：担心 OOM 的场景
 *   <li>图片缓存：Android 开发常用
 *   <li>文档缓存：大对象缓存
 *   <li>页面缓存：Web 页面缓存
 * </ul>
 *
 * <p>与 WeakReference 对比：
 *
 * <ul>
 *   <li>软引用：内存不足时回收，适合缓存
 *   <li>弱引用：GC 发现即回收，适合元数据缓存
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @since 1.0.0
 * 
 */
public class SoftValueCache<K, V> extends AbstractCache<K, V> {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(SoftValueCache.class);

  /** 底层并发存储映射 */
  private final ConcurrentMap<K, SoftReference<V>> map;

  /** 软引用队列，用于跟踪被 GC 回收的值 */
  private final ReferenceQueue<V> queue;

  /** 清理时间间隔阈值（纳秒） */
  private static final long CLEANUP_INTERVAL_NANOS = 1_000_000L;

  /** 上次清理时间 */
  private volatile long lastCleanupTime = 0;

  public SoftValueCache() {
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
      log.debug("SoftValueCache GC 清理完成，移除引用数={}", removed[0]);
    }
  }

  @Override
  public V getIfPresent(K key) {
    maybeCleanup();
    SoftReference<V> ref = map.get(key);
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
    SoftReference<V> ref = new SoftReference<>(value, queue);
    map.put(key, ref);
  }

  @Override
  public V remove(K key) {
    maybeCleanup();
    SoftReference<V> ref = map.remove(key);
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
    SoftReference<V> ref = map.get(key);
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
    for (SoftReference<V> ref : map.values()) {
      V value = ref.get();
      if (value != null) {
        list.add(value);
      }
    }
    return list;
  }
}
