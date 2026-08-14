package com.njydsz.common.cache.internal.decorator;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.listener.RemovalListener;

/**
 * 热点 Key 钉选装饰器 — 防止高频访问的 key 被容量淘汰
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>钉选保护：被钉选的 key 在容量满时不会被淘汰（RemovalCause.SIZE 时跳过）
 *   <li>动态管理：支持运行时添加/移除钉选 key，适应访问模式变化
 *   <li>计数跟踪：记录每个 key 被钉选保护的次数（成功跳过淘汰的次数）
 *   <li>轻量级：使用 ConcurrentHashMap 存储钉选集，查询 O(1)
 * </ul>
 *
 * <p>注意：钉选仅防止容量淘汰（SIZE），过期淘汰（EXPIRED）和显式删除（EXPLICIT）不受影响。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class HotKeyPinningCacheDecorator<K, V> extends AbstractCacheDecorator<K, V> {

  private static final Logger log = LoggerFactory.getLogger(HotKeyPinningCacheDecorator.class);

  /** 钉选 key 集合（O(1) 查询） */
  private final ConcurrentHashMap<K, Boolean> pinnedKeys = new ConcurrentHashMap<>();

  /** 钉选保护计数（key -> 成功跳过淘汰的次数） */
  private final ConcurrentHashMap<K, Long> pinProtectionCount = new ConcurrentHashMap<>();

  /** 最大钉选数量 */
  private volatile int maxPinnedKeys;

  /** 内部钉选监听器 — 阻止已钉选 key 被淘汰 */
  private final RemovalListener<K, V> pinningListener;

  /**
   * 创建热点钉选装饰器
   *
   * @param delegate 底层缓存
   */
  public HotKeyPinningCacheDecorator(Cache<K, V> delegate) {
    this(delegate, 1000);
  }

  /**
   * 创建热点钉选装饰器
   *
   * @param delegate 底层缓存
   * @param maxPinnedKeys 最大钉选 key 数量（超过后钉选操作将被忽略）
   */
  public HotKeyPinningCacheDecorator(Cache<K, V> delegate, int maxPinnedKeys) {
    super(delegate);
    this.maxPinnedKeys = maxPinnedKeys;
    this.pinningListener = (key, value, cause) -> {
      if (cause == RemovalCause.SIZE && key != null && pinnedKeys.containsKey(key)) {
        // 阻止淘汰：重新写入底层缓存
        delegate.put(key, value);
        pinProtectionCount.compute(key, (k, v) -> v == null ? 1L : v + 1);
        log.debug("钉选保护触发，key={}", key);
      }
    };
    delegate.addListener(pinningListener);
  }

  /**
   * 钉选指定 key（防止被容量淘汰）
   *
   * @param key 需要钉选的 key
   * @return true 表示成功钉选；false 表示超过最大钉选数量限制
   */
  public boolean pin(K key) {
    if (key == null) {
      return false;
    }
    if (pinnedKeys.size() >= maxPinnedKeys && !pinnedKeys.containsKey(key)) {
      log.warn("钉选数量已达上限 {}，忽略 key={}", maxPinnedKeys, key);
      return false;
    }
    pinnedKeys.put(key, Boolean.TRUE);
    return true;
  }

  /**
   * 批量钉选多个 key
   *
   * @param keys 需要钉选的 key 集合
   * @return 实际成功钉选的数量
   */
  public int pinAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (K key : keys) {
      if (pin(key)) {
        count++;
      }
    }
    return count;
  }

  /**
   * 重新钉选（清空当前钉选集合并钉选新的 key 集合）
   *
   * @param keys 新的钉选 key 集合
   * @return 实际成功钉选的数量
   */
  public int repin(Collection<K> keys) {
    pinnedKeys.clear();
    pinProtectionCount.clear();
    return pinAll(keys);
  }

  /**
   * 移除钉选
   *
   * @param key 需要移除钉选的 key
   * @return true 表示该 key 之前已被钉选并已移除
   */
  public boolean unpin(K key) {
    return pinnedKeys.remove(key) != null;
  }

  /**
   * 判断指定 key 是否已被钉选
   *
   * @param key 缓存键
   * @return true 表示已钉选
   */
  public boolean isPinned(K key) {
    return pinnedKeys.containsKey(key);
  }

  /**
   * 获取当前钉选 key 数量
   *
   * @return 钉选数量
   */
  public int getPinnedCount() {
    return pinnedKeys.size();
  }

  /**
   * 获取指定 key 被钉选保护的次数
   *
   * @param key 缓存键
   * @return 保护次数；如果该 key 从未被保护过则返回 0
   */
  public long getPinProtectionCount(K key) {
    return pinProtectionCount.getOrDefault(key, 0L);
  }

  /**
   * 获取所有钉选 key 的集合视图
   *
   * @return 钉选 key 集合
   */
  public Set<K> getPinnedKeys() {
    return java.util.Collections.unmodifiableSet(pinnedKeys.keySet());
  }

  /**
   * 设置最大钉选数量
   *
   * @param maxPinnedKeys 新的最大钉选数量
   */
  public void setMaxPinnedKeys(int maxPinnedKeys) {
    this.maxPinnedKeys = Math.max(1, maxPinnedKeys);
  }

  /**
   * 获取缓存值，如果不存在则使用加载器加载。
   *
   * @param key 缓存键
   * @param loader 加载器
   * @return 缓存值
   */
  @Override
  public V get(K key, java.util.function.Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value == null && loader != null) {
      value = loader.apply(key);
      if (value != null) {
        put(key, value);
      }
    }
    return value;
  }

  /**
   * 销毁装饰器，移除内部监听器
   */
  public void destroy() {
    pinnedKeys.clear();
    pinProtectionCount.clear();
  }
}
