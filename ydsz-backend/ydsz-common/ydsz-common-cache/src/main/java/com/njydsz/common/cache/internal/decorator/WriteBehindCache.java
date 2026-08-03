package com.njydsz.common.cache.internal.decorator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;
import com.njydsz.common.cache.support.CacheThreadPoolManager;
import com.njydsz.common.cache.support.CacheWriter;

/**
 * Write-Behind 缓存装饰器 — 异步写回模式
 *
 * <p>写入操作先更新缓存，然后异步批量写入后端存储。 相比 Write-Through 模式，
 * Write-Behind 提供更高的写入吞吐量，但有数据丢失风险。
 *
 * <p>工作原理：
 *
 * <ol>
 *   <li>put：先写入缓存，然后将写入操作加入队列
 *   <li>后台线程定期从队列批量取出操作，写入后端存储
 *   <li>如果队列满，降级为同步写入
 * </ol>
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>高写入吞吐场景（如计数器、日志）
 *   <li>可容忍短暂数据不一致的场景
 *   <li>后端存储写入延迟较高的场景
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * 
 * @since 1.0.0
 */
public class WriteBehindCache<K, V> implements Cache<K, V>, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(WriteBehindCache.class);

  private final Cache<K, V> delegate;
  private final CacheWriter<? super K, ? super V> writer;
  private final ScheduledExecutorService batchExecutor;
  private final int batchSize;

  /** 待写入队列 */
  private final ConcurrentLinkedQueue<WriteOp<K, V>> writeQueue = new ConcurrentLinkedQueue<>();

  /** 最大重试次数 */
  private static final int MAX_RETRY = 3;

  /** 重试延迟（毫秒） */
  private static final long RETRY_DELAY_MS = 100;

  /** 死信队列：写入失败的操作 */
  private final ConcurrentLinkedQueue<WriteOp<K, V>> deadLetterQueue = new ConcurrentLinkedQueue<>();

  /** 统计 */
  private final AtomicLong asyncWriteCount = new AtomicLong(0);
  private final AtomicLong syncFallbackCount = new AtomicLong(0);
  private final AtomicLong batchFlushCount = new AtomicLong(0);
  private final AtomicLong queueOverflowCount = new AtomicLong(0);
  private final AtomicLong retryCount = new AtomicLong(0);
  private final AtomicLong deadLetterCount = new AtomicLong(0);

  /** 最大队列长度（超过后降级为同步写入） */
  private final int maxQueueSize;

  /** 写操作类型 */
  private enum OpType {
    WRITE,
    DELETE
  }

  /** 写操作 */
  private static class WriteOp<K, V> {
    final OpType type;
    final K key;
    final V value;

    WriteOp(OpType type, K key, V value) {
      this.type = type;
      this.key = key;
      this.value = value;
    }
  }

  /**
   * 创建 Write-Behind 缓存
   *
   * @param delegate 底层缓存
   * @param writer 后端写入器
   * @param executor 异步写入执行器
   * @param flushIntervalMs 批量刷新间隔（毫秒）
   * @param batchSize 每批最大写入数量
   * @param maxQueueSize 最大队列长度
   */
  public WriteBehindCache(
      Cache<K, V> delegate,
      CacheWriter<? super K, ? super V> writer,
      Executor executor,
      long flushIntervalMs,
      int batchSize,
      int maxQueueSize) {
    this.delegate = delegate;
    this.writer = writer;
    this.batchSize = batchSize;
    this.maxQueueSize = maxQueueSize;

    this.batchExecutor =
        CacheThreadPoolManager.getInstance().getOrCreateScheduledPool("write-behind-flusher", 1);
    this.batchExecutor.scheduleWithFixedDelay(
        this::flushBatch, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);

    log.info("WriteBehindCache 已创建, flushInterval={}ms, batchSize={}, maxQueue={}",
        flushIntervalMs, batchSize, maxQueueSize);
  }

  /**
   * 写入键值对：先更新缓存，再异步排队写回后端。
   *
   * <p>队列未满时写入操作入队（异步写回）；队列已满时降级为同步写后端以控制内存占用。
   * 注意：缓存先更新成功而后端异步写入，存在短暂不一致与进程崩溃时的数据丢失风险。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    // 先写缓存
    delegate.put(key, value);

    if (writeQueue.size() >= maxQueueSize) {
      // 队列满，降级为同步写入
      queueOverflowCount.incrementAndGet();
      syncFallbackCount.incrementAndGet();
      try {
        writer.write(key, value);
      } catch (Exception e) {
        log.warn("Write-Behind 同步降级写入失败: key={}", key, e);
      }
    } else {
      writeQueue.offer(new WriteOp<>(OpType.WRITE, key, value));
      asyncWriteCount.incrementAndGet();
    }
  }

  /**
   * 移除指定键并返回被移除的值，同时异步排队删除后端数据。
   *
   * <p>仅在键真实存在（返回值非 null）时才产生 DELETE 写操作， 避免无谓的后端删除调用。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    V value = delegate.remove(key);
    if (value != null) {
      writeQueue.offer(new WriteOp<>(OpType.DELETE, key, value));
    }
    return value;
  }

  /** 批量刷新队列中的写操作到后端存储（带重试 + 死信） */
  private void flushBatch() {
    int count = 0;
    WriteOp<K, V> op;
    while (count < batchSize && (op = writeQueue.poll()) != null) {
      boolean success = false;
      for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
        try {
          if (op.type == OpType.WRITE) {
            writer.write(op.key, op.value);
          } else {
            writer.delete(op.key, op.value);
          }
          success = true;
          break;
        } catch (Exception e) {
          if (attempt < MAX_RETRY - 1) {
            retryCount.incrementAndGet();
            log.warn("Write-Behind 写入重试 {}/{}: key={}, op={}",
                attempt + 1, MAX_RETRY, op.key, op.type, e);
            try {
              Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              break;
            }
          } else {
            log.error("Write-Behind 写入失败，进入死信队列: key={}, op={}", op.key, op.type, e);
          }
        }
      }
      if (!success) {
        deadLetterQueue.offer(op);
        deadLetterCount.incrementAndGet();
      }
      count++;
    }
    if (count > 0) {
      batchFlushCount.incrementAndGet();
      log.debug("Write-Behind 批量刷新: count={}", count);
    }
  }

  /** 获取统计信息 */
  public Map<String, Long> getWriteBehindStats() {
    Map<String, Long> stats = new HashMap<>();
    stats.put("asyncWriteCount", asyncWriteCount.get());
    stats.put("syncFallbackCount", syncFallbackCount.get());
    stats.put("batchFlushCount", batchFlushCount.get());
    stats.put("queueOverflowCount", queueOverflowCount.get());
    stats.put("retryCount", retryCount.get());
    stats.put("deadLetterCount", deadLetterCount.get());
    stats.put("queueSize", (long) writeQueue.size());
    stats.put("deadLetterQueueSize", (long) deadLetterQueue.size());
    return stats;
  }

  /** 获取死信队列中的操作（用于人工补偿） */
  public List<WriteOp<K, V>> drainDeadLetterQueue() {
    List<WriteOp<K, V>> result = new ArrayList<>();
    WriteOp<K, V> op;
    while ((op = deadLetterQueue.poll()) != null) {
      result.add(op);
    }
    return result;
  }

  /**
   * 关闭 Write-Behind 缓存。
   *
   * <p>先同步刷新队列中剩余的写操作（带重试与死信处理），确保关闭时尽量不丢数据；
   * 线程池由 {@link CacheThreadPoolManager} 统一管理，不在此关闭。
   */
  @Override
  public void close() {
    // 刷新剩余操作
    flushBatch();
    // 线程池由 CacheThreadPoolManager 统一管理，不单独关闭
    log.info("WriteBehindCache 已关闭");
  }

  // === 以下方法委托给底层缓存 ===

  /**
   * 获取缓存值（不触发加载）。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

  /**
   * 获取缓存值，未命中时使用加载器加载。
   *
   * @param key    缓存键
   * @param loader 值加载器
   * @return 缓存值或加载的新值
   */
  @Override
  public V get(K key, Function<K, V> loader) {
    return delegate.get(key, loader);
  }

  /**
   * 异步获取缓存值，未命中时使用异步加载器加载。
   *
   * @param key    缓存键
   * @param loader 异步值加载器
   * @return 异步完成的缓存值
   */
  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return delegate.getAsync(key, loader);
  }

  /**
   * 仅当键不存在时写入。
   *
   * <p>注意：直接委托底层缓存，不排队写后端，调用方需自行保证后端一致性。
   *
   * @param key   缓存键
   * @param value 缓存值
   * @return 已存在的旧值；键原本不存在时返回 {@code null}
   */
  @Override
  public V putIfAbsent(K key, V value) {
    return delegate.putIfAbsent(key, value);
  }

  /**
   * 计算并写入缓存（直接委托，不排队写后端）。
   *
   * @param key             缓存键
   * @param mappingFunction 映射函数
   * @return 计算后的值
   */
  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  /**
   * 基于旧值重新计算映射并写回缓存（直接委托，不排队写后端）。
   *
   * @param key               缓存键
   * @param remappingFunction 重映射函数
   * @return 重映射后的值
   */
  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return delegate.compute(key, remappingFunction);
  }

  /**
   * 合并值与现有值（直接委托，不排队写后端）。
   *
   * @param key               缓存键
   * @param value             待合并的值
   * @param remappingFunction 合并函数
   * @return 合并后的值
   */
  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    return delegate.merge(key, value, remappingFunction);
  }

  /**
   * 清空缓存与待写队列。
   *
   * <p>已入队的写操作被丢弃不再写回后端，调用方需注意数据一致性问题。
   */
  @Override
  public void clear() {
    delegate.clear();
    writeQueue.clear();
  }

  /**
   * 批量写入：先写缓存，再逐条排队写回后端（队列满时同步降级）。
   *
   * @param map 待写入的映射
   */
  @Override
  public void putAll(Map<K, V> map) {
    delegate.putAll(map);
    long now = System.nanoTime();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      if (writeQueue.size() >= maxQueueSize) {
        // 队列满，降级为同步写入
        queueOverflowCount.incrementAndGet();
        syncFallbackCount.incrementAndGet();
        try {
          writer.write(entry.getKey(), entry.getValue());
        } catch (Exception e) {
          log.warn("Write-Behind 批量同步降级写入失败: key={}", entry.getKey(), e);
        }
      } else {
        writeQueue.offer(new WriteOp<>(OpType.WRITE, entry.getKey(), entry.getValue()));
        asyncWriteCount.incrementAndGet();
      }
    }
  }

  /**
   * 批量获取指定键的缓存值（不触发加载）。
   *
   * @param keys 待获取的键集合
   * @return 命中键值映射；未命中的键不会出现在结果中
   */
  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    return delegate.getAll(keys);
  }

  /**
   * 批量移除指定键，并为每个键排队 DELETE 写操作。
   *
   * @param keys 待移除的键集合
   */
  @Override
  public void removeAll(Collection<K> keys) {
    delegate.removeAll(keys);
    keys.forEach(k -> writeQueue.offer(new WriteOp<>(OpType.DELETE, k, null)));
  }

  /**
   * 使单个键失效（等价于 {@link #remove}，会异步删除后端数据）。
   *
   * @param key 缓存键
   */
  @Override
  public void invalidate(K key) {
    remove(key);
  }

  /**
   * 批量使指定键集合失效（等价于 {@link #removeAll}）。
   *
   * @param keys 待失效的键集合
   */
  @Override
  public void invalidateAll(Collection<K> keys) {
    removeAll(keys);
  }

  /**
   * 使全部键失效（等价于 {@link #clear}）。
   */
  @Override
  public void invalidateAll() {
    clear();
  }

  /**
   * 返回缓存条目数（近似值）。
   *
   * @return 底层缓存条目数
   */
  @Override
  public long estimatedSize() {
    return delegate.estimatedSize();
  }

  /**
   * 判断缓存是否为空。
   *
   * @return 底层缓存无条目时返回 {@code true}
   */
  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  /**
   * 获取缓存命中率。
   *
   * @return 底层缓存的命中率
   */
  @Override
  public double getHitRate() {
    return delegate.getHitRate();
  }

  /**
   * 获取缓存统计快照。
   *
   * @return 底层缓存的统计对象
   */
  @Override
  public CacheStats getStats() {
    return delegate.getStats();
  }

  /**
   * 重置统计计数器。
   */
  @Override
  public void resetStats() {
    delegate.resetStats();
  }

  /**
   * 获取缓存策略查询接口。
   *
   * @return 底层缓存的策略接口
   */
  @Override
  public CachePolicy policy() {
    return delegate.policy();
  }

  /**
   * 判断缓存中是否存在指定键。
   *
   * @param key 缓存键
   * @return 底层缓存存在该键时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    return delegate.containsKey(key);
  }

  /**
   * 返回缓存键集合视图。
   *
   * @return 底层缓存的键集合视图
   */
  @Override
  public Set<K> keySet() {
    return delegate.keySet();
  }

  /**
   * 返回缓存值集合视图。
   *
   * @return 底层缓存的值集合视图
   */
  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  /**
   * 执行缓存维护操作（清理过期条目等）。
   */
  @Override
  public void cleanUp() {
    delegate.cleanUp();
  }

  /**
   * 添加删除监听器。
   *
   * @param listener 删除监听器
   */
  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  /**
   * 遍历缓存键值对。
   *
   * @param action 作用于每个键值对的消费动作
   */
  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    delegate.forEach(action);
  }
}
