package com.njydsz.common.safe.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 基于 ConcurrentHashMap + TLS 的 {@link SafeCache} 兜底实现。
 *
 * <p>当 ydzs-common-cache 不在 classpath 时，safe 模块使用此实现替代 Caffeine 语义的缓存。 采用 expireAfterWrite 语义：每次写入记录当前时间戳，读取或定时清理时判断是否过期。
 *
 * <p>容量限制通过拒绝新写入实现（fail-closed 安全语义：缓存满时拒绝新 nonce/CSRF token，避免内存膨胀但保障安全功能可用）。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 26.09.01
 */
public class ConcurrentTtlSafeCache<K, V> implements SafeCache<K, V> {

  private static final Logger LOG = LoggerFactory.getLogger(ConcurrentTtlSafeCache.class);

  private final ConcurrentMap<K, TimestampedValue<V>> map = new ConcurrentHashMap<>();
  private final long ttlMillis;
  private final long maxSize;
  private final AtomicLong putCount = new AtomicLong(0);

  /**
   * 构造 TTL 缓存
   *
   * @param expireAfterWrite 写入后过期时间
   * @param timeUnit 时间单位
   * @param maxSize 最大条目数（0 表示不限制）
   */
  public ConcurrentTtlSafeCache(long expireAfterWrite, TimeUnit timeUnit, long maxSize) {
    this.ttlMillis = timeUnit.toMillis(expireAfterWrite);
    this.maxSize = maxSize;
    LOG.info(
        "ConcurrentTtlSafeCache 已初始化: ttl={}ms, maxSize={}",
        this.ttlMillis,
        this.maxSize > 0 ? this.maxSize : "unlimited");
  }

  /** 构造 TTL 缓存（默认不限制容量） */
  public ConcurrentTtlSafeCache(long expireAfterWrite, TimeUnit timeUnit) {
    this(expireAfterWrite, timeUnit, 0);
  }

  @Override
  @SuppressWarnings("unchecked")
  public V getIfPresent(K key) {
    if (key == null) {
      return null;
    }
    TimestampedValue<V> holder = map.get(key);
    if (holder == null) {
      return null;
    }
    if (isExpired(holder)) {
      map.remove(key, holder);
      return null;
    }
    return holder.value;
  }

  @Override
  public void put(K key, V value) {
    if (key == null) {
      return;
    }
    if (maxSize > 0 && map.size() >= maxSize && !map.containsKey(key)) {
      // fail-closed: 缓存满时拒绝新写入，避免内存膨胀
      LOG.warn(
          "ConcurrentTtlSafeCache 已达容量上限 ({}), 拒绝写入: key={}", maxSize, key);
      return;
    }
    map.put(key, new TimestampedValue<>(value, System.currentTimeMillis()));
    putCount.incrementAndGet();
  }

  @Override
  public V putIfAbsent(K key, V value) {
    if (key == null) {
      return null;
    }
    TimestampedValue<V> newHolder = new TimestampedValue<>(value, System.currentTimeMillis());
    TimestampedValue<V> existing = map.putIfAbsent(key, newHolder);
    if (existing != null) {
      if (isExpired(existing)) {
        if (map.replace(key, existing, newHolder)) {
          putCount.incrementAndGet();
          return null;
        }
        return map.get(key).value;
      }
      return existing.value;
    }
    putCount.incrementAndGet();
    return null;
  }

  @Override
  public void invalidate(K key) {
    if (key != null) {
      map.remove(key);
    }
  }

  @Override
  public void invalidateAll() {
    map.clear();
    LOG.info("ConcurrentTtlSafeCache 已清空");
  }

  @Override
  public long estimatedSize() {
    return map.size();
  }

  /**
   * 定时清理过期条目（由宿主应用 @EnableScheduling 驱动）
   *
   * <p>默认 60 秒间隔，可通过 ydsz.safe.clean-interval 配置
   */
  @Scheduled(
      fixedRateString = "${ydsz.safe.clean-interval:60000}",
      initialDelayString = "${ydsz.safe.clean-initial-delay:60000}")
  @Override
  public void cleanUp() {
    long before = map.size();
    Iterator<Map.Entry<K, TimestampedValue<V>>> it = map.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<K, TimestampedValue<V>> entry = it.next();
      if (isExpired(entry.getValue())) {
        it.remove();
      }
    }
    long after = map.size();
    long cleaned = before - after;
    if (cleaned > 0) {
      LOG.info(
          "ConcurrentTtlSafeCache 定时清理完成: 清理前={}, 清理后={}, 清理数量={}",
          before,
          after,
          cleaned);
    }
  }

  /**
   * 获取累计写入次数
   *
   * @return 写入次数
   */
  public long getPutCount() {
    return putCount.get();
  }

  private boolean isExpired(TimestampedValue<V> holder) {
    return System.currentTimeMillis() - holder.timestamp > ttlMillis;
  }

  /** 带时间戳的值包装（不可变） */
  static final class TimestampedValue<V> {
    final V value;
    final long timestamp;

    TimestampedValue(V value, long timestamp) {
      this.value = value;
      this.timestamp = timestamp;
    }
  }
}
