package com.njydsz.pmis.common.cache.internal.decorator;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;
import com.njydsz.pmis.common.cache.support.CacheWriter;

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
 * @author Marvin Lee
 * @version 4.1.0
 */
public class WriteBehindCache<K, V> implements Cache<K, V>, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(WriteBehindCache.class);

  private final Cache<K, V> delegate;
  private final CacheWriter<K, V> writer;
  private final Executor executor;
  private final ScheduledExecutorService batchExecutor;
  private final long flushIntervalMs;
  private final int batchSize;

  /** 待写入队列 */
  private final ConcurrentLinkedQueue<WriteOp<K, V>> writeQueue = new ConcurrentLinkedQueue<>();

  /** 统计 */
  private final AtomicLong asyncWriteCount = new AtomicLong(0);
  private final AtomicLong syncFallbackCount = new AtomicLong(0);
  private final AtomicLong batchFlushCount = new AtomicLong(0);
  private final AtomicLong queueOverflowCount = new AtomicLong(0);

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
      CacheWriter<K, V> writer,
      Executor executor,
      long flushIntervalMs,
      int batchSize,
      int maxQueueSize) {
    this.delegate = delegate;
    this.writer = writer;
    this.executor = executor != null ? executor : ForkJoinPool.commonPool();
    this.flushIntervalMs = flushIntervalMs;
    this.batchSize = batchSize;
    this.maxQueueSize = maxQueueSize;

    this.batchExecutor = new ScheduledThreadPoolExecutor(1, r -> {
      Thread t = new Thread(r, "WriteBehind-Flusher");
      t.setDaemon(true);
      return t;
    });
    this.batchExecutor.scheduleWithFixedDelay(
        this::flushBatch, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);

    log.info("WriteBehindCache 已创建, flushInterval={}ms, batchSize={}, maxQueue={}",
        flushIntervalMs, batchSize, maxQueueSize);
  }

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

  @Override
  public V remove(K key) {
    V value = delegate.remove(key);
    if (value != null) {
      writeQueue.offer(new WriteOp<>(OpType.DELETE, key, value));
    }
    return value;
  }

  /** 批量刷新队列中的写操作到后端存储 */
  private void flushBatch() {
    int count = 0;
    WriteOp<K, V> op;
    while (count < batchSize && (op = writeQueue.poll()) != null) {
      try {
        if (op.type == OpType.WRITE) {
          writer.write(op.key, op.value);
        } else {
          writer.delete(op.key, op.value);
        }
        count++;
      } catch (Exception e) {
        log.warn("Write-Behind 批量写入失败: key={}, op={}", op.key, op.type, e);
      }
    }
    if (count > 0) {
      batchFlushCount.incrementAndGet();
      log.debug("Write-Behind 批量刷新: count={}", count);
    }
  }

  /** 获取统计信息 */
  public Map<String, Long> getWriteBehindStats() {
    Map<String, Long> stats = new java.util.HashMap<>();
    stats.put("asyncWriteCount", asyncWriteCount.get());
    stats.put("syncFallbackCount", syncFallbackCount.get());
    stats.put("batchFlushCount", batchFlushCount.get());
    stats.put("queueOverflowCount", queueOverflowCount.get());
    stats.put("queueSize", (long) writeQueue.size());
    return stats;
  }

  @Override
  public void close() {
    // 刷新剩余操作
    flushBatch();
    batchExecutor.shutdown();
    try {
      batchExecutor.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    log.info("WriteBehindCache 已关闭");
  }

  // === 以下方法委托给底层缓存 ===

  @Override
  public V getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    return delegate.get(key, loader);
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return delegate.getAsync(key, loader);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    return delegate.putIfAbsent(key, value);
  }

  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return delegate.compute(key, remappingFunction);
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    return delegate.merge(key, value, remappingFunction);
  }

  @Override
  public void clear() {
    delegate.clear();
    writeQueue.clear();
  }

  @Override
  public void putAll(Map<K, V> map) {
    delegate.putAll(map);
    long now = System.currentTimeMillis();
    map.forEach((k, v) -> writeQueue.offer(new WriteOp<>(OpType.WRITE, k, v)));
  }

  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    return delegate.getAll(keys);
  }

  @Override
  public void removeAll(Collection<K> keys) {
    delegate.removeAll(keys);
    keys.forEach(k -> writeQueue.offer(new WriteOp<>(OpType.DELETE, k, null)));
  }

  @Override
  public void invalidate(K key) {
    remove(key);
  }

  @Override
  public void invalidateAll(Collection<K> keys) {
    removeAll(keys);
  }

  @Override
  public void invalidateAll() {
    clear();
  }

  @Override
  public long estimatedSize() {
    return delegate.estimatedSize();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public double getHitRate() {
    return delegate.getHitRate();
  }

  @Override
  public CacheStats getStats() {
    return delegate.getStats();
  }

  @Override
  public void resetStats() {
    delegate.resetStats();
  }

  @Override
  public com.njydsz.pmis.common.cache.api.CachePolicy policy() {
    return delegate.policy();
  }

  @Override
  public boolean containsKey(K key) {
    return delegate.containsKey(key);
  }

  @Override
  public Set<K> keySet() {
    return delegate.keySet();
  }

  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  @Override
  public void cleanUp() {
    delegate.cleanUp();
  }

  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    delegate.forEach(action);
  }
}
