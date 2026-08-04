package com.remisoft.common.cache.internal.reference;

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

import com.remisoft.common.cache.internal.AbstractCache;
import com.remisoft.common.cache.listener.RemovalCause;

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
 * @author remi-team
 * @since 1.0.0
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

  /**
   * 获取缓存值（不触发加载）。
   *
   * <p>值被 GC 回收后等价于未命中：返回 null 并计入 miss。访问前会触发轻量级过期清理
   * （受 {@link #CLEANUP_INTERVAL_NANOS} 节流）。
   *
   * @param key 缓存键
   * @return 缓存值；未命中或软引用已被 GC 回收时返回 {@code null}
   */
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

  /**
   * 写入键值对，值以软引用形式存储。
   *
   * <p>值对象在 JVM 内存不足时可被 GC 回收（不阻塞回收）， 键的软引用注册到引用队列，便于后续清理残留条目。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    maybeCleanup();
    SoftReference<V> ref = new SoftReference<>(value, queue);
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
    SoftReference<V> ref = map.remove(key);
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
   * <p>与底层 Map 大小不同，已回收值的条目会被过滤，结果随 GC 动态变化。
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
    SoftReference<V> ref = map.get(key);
    return ref != null && ref.get() != null;
  }

  /**
   * 返回缓存键集合。
   *
   * <p>透传底层并发映射的键视图（弱一致），可能包含值已被 GC 回收的残留键。
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
    for (SoftReference<V> ref : map.values()) {
      V value = ref.get();
      if (value != null) {
        list.add(value);
      }
    }
    return list;
  }
}
