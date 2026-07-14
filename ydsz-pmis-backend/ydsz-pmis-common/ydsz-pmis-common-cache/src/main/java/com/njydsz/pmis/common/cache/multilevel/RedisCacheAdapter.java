package com.njydsz.pmis.common.cache.multilevel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;

/**
 * Redis L2 缓存适配器 — 将 RedisTemplate 适配为 YdszCache Cache 接口
 *
 * <p>作为多级缓存的 L2 后端，提供分布式缓存能力。 使用 Spring Data Redis 的 RedisTemplate 进行序列化/反序列化。
 *
 * <p>特性：
 *
 * <ul>
 *   <li>支持 TTL 过期（写入时设置）
 *   <li>支持批量读写（getAll/putAll）
 *   <li>独立统计计数（命中/未命中/写入/删除）
 *   <li>key 前缀隔离（避免不同缓存实例 key 冲突）
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author Marvin Lee
 * @version 4.0.0
 */
public class RedisCacheAdapter<K, V> implements Cache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(RedisCacheAdapter.class);

  private final RedisTemplate<String, Object> redisTemplate;
  private final String keyPrefix;
  private final long ttlSeconds;
  private final Class<V> valueClass;

  private final LongAdder hitCount = new LongAdder();
  private final LongAdder missCount = new LongAdder();
  private final LongAdder writeCount = new LongAdder();
  private final LongAdder deleteCount = new LongAdder();

  /**
   * 创建 Redis 缓存适配器
   *
   * @param redisTemplate Redis 模板
   * @param keyPrefix key 前缀（用于隔离不同缓存实例）
   * @param ttlSeconds TTL 过期时间（秒），0 表示永不过期
   * @param valueClass 值类型（用于反序列化）
   */
  public RedisCacheAdapter(
      RedisTemplate<String, Object> redisTemplate,
      String keyPrefix,
      long ttlSeconds,
      Class<V> valueClass) {
    this.redisTemplate = redisTemplate;
    this.keyPrefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    this.ttlSeconds = ttlSeconds;
    this.valueClass = valueClass;
  }

  /** 构建 Redis key */
  private String buildKey(K key) {
    return keyPrefix + key.toString();
  }

  @Override
  public V getIfPresent(K key) {
    try {
      Object value = redisTemplate.opsForValue().get(buildKey(key));
      if (value != null) {
        hitCount.increment();
        return valueClass.isInstance(value) ? valueClass.cast(value) : null;
      }
      missCount.increment();
      return null;
    } catch (Exception e) {
      log.warn("Redis 缓存读取失败, key={}", key, e);
      missCount.increment();
      return null;
    }
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value == null && loader != null) {
      value = loader.apply(key);
      if (value != null) {
        put(key, value);
      }
    }
    return value;
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    V value = getIfPresent(key);
    if (value != null) {
      return CompletableFuture.completedFuture(value);
    }
    return loader
        .apply(key)
        .thenApply(
            v -> {
              if (v != null) {
                put(key, v);
              }
              return v;
            });
  }

  @Override
  public void put(K key, V value) {
    try {
      String redisKey = buildKey(key);
      if (ttlSeconds > 0) {
        redisTemplate.opsForValue().set(redisKey, value, Duration.ofSeconds(ttlSeconds));
      } else {
        redisTemplate.opsForValue().set(redisKey, value);
      }
      writeCount.increment();
    } catch (Exception e) {
      log.warn("Redis 缓存写入失败, key={}", key, e);
    }
  }

  @Override
  public V remove(K key) {
    V value = getIfPresent(key);
    try {
      redisTemplate.delete(buildKey(key));
      deleteCount.increment();
    } catch (Exception e) {
      log.warn("Redis 缓存删除失败, key={}", key, e);
    }
    return value;
  }

  @Override
  public void clear() {
    // 使用 SCAN 替代 KEYS，避免阻塞 Redis
    Set<String> keys = scanKeys(keyPrefix + "*", 1000);
    if (!keys.isEmpty()) {
      try {
        redisTemplate.delete(keys);
      } catch (Exception e) {
        log.warn("Redis 缓存清空失败, prefix={}", keyPrefix, e);
      }
    }
  }

  @Override
  public long estimatedSize() {
    // 使用 SCAN 替代 KEYS 计数
    return scanKeys(keyPrefix + "*", 1000).size();
  }

  /**
   * 使用 SCAN 命令扫描匹配的 key（非阻塞，替代 KEYS）
   *
   * @param pattern key 匹配模式
   * @param count 每次扫描的建议数量
   * @return 匹配的 key 集合
   */
  private Set<String> scanKeys(String pattern, int count) {
    Set<String> keys = new HashSet<>();
    try {
      ScanOptions options = ScanOptions.scanOptions().match(pattern).count(count).build();
      Cursor<byte[]> cursor =
          redisTemplate.execute(
              (RedisCallback<Cursor<byte[]>>)
                  connection -> connection.keyCommands().scan(options));
      while (cursor.hasNext()) {
        keys.add(new String(cursor.next()));
      }
      cursor.close();
    } catch (Exception e) {
      log.warn("Redis SCAN 扫描失败, pattern={}", pattern, e);
    }
    return keys;
  }

  @Override
  public boolean containsKey(K key) {
    try {
      Boolean exists = redisTemplate.hasKey(buildKey(key));
      return Boolean.TRUE.equals(exists);
    } catch (Exception e) {
      log.warn("Redis 缓存检查失败, key={}", key, e);
      return false;
    }
  }

  @Override
  public Set<K> keySet() {
    // Redis 不支持高效获取所有 key，返回空集合
    return Set.of();
  }

  @Override
  public Collection<V> values() {
    return Set.of();
  }

  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return new HashMap<>();
    }
    // 使用 multiGet 批量查询，替代逐个 getIfPresent
    List<K> keyList = new ArrayList<>(keys);
    List<String> redisKeys =
        keyList.stream().map(this::buildKey).collect(Collectors.toList());
    try {
      List<Object> values = redisTemplate.opsForValue().multiGet(redisKeys);
      Map<K, V> result = new HashMap<>(keys.size());
      if (values != null) {
        for (int i = 0; i < values.size(); i++) {
          Object value = values.get(i);
          if (value != null && valueClass.isInstance(value)) {
            result.put(keyList.get(i), valueClass.cast(value));
            hitCount.increment();
          } else {
            missCount.increment();
          }
        }
      }
      return result;
    } catch (Exception e) {
      log.warn("Redis 批量缓存读取失败", e);
      return new HashMap<>();
    }
  }

  @Override
  public void putAll(Map<K, V> map) {
    map.forEach(this::put);
  }

  @Override
  public void removeAll(Collection<K> keys) {
    keys.forEach(this::remove);
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
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    V value = getIfPresent(key);
    if (value == null) {
      value = mappingFunction.apply(key);
      if (value != null) {
        put(key, value);
      }
    }
    return value;
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    V oldValue = getIfPresent(key);
    V newValue = remappingFunction.apply(key, oldValue);
    if (newValue != null) {
      put(key, newValue);
    } else {
      remove(key);
    }
    return newValue;
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    // Redis 不支持高效遍历，空实现
  }

  @Override
  public double getHitRate() {
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0.0 : (double) hitCount.sum() / total;
  }

  @Override
  public CacheStats getStats() {
    return new CacheStats(hitCount.sum(), missCount.sum());
  }

  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    // Redis 缓存不支持删除监听器
  }

  @Override
  public void cleanUp() {
    // Redis 自动过期，无需手动清理
  }

  /** 获取写入次数 */
  public long getWriteCount() {
    return writeCount.sum();
  }

  /** 获取删除次数 */
  public long getDeleteCount() {
    return deleteCount.sum();
  }
}
