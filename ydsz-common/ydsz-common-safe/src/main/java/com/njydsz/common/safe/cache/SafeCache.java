package com.njydsz.common.safe.cache;

/**
 * 安全模块内部缓存抽象。
 *
 * <p>解耦 safe 模块对 ydzz-common-cache 的编译期依赖：使用方不引入 common-cache 时， 自动退化为基于 {@link java.util.concurrent.ConcurrentHashMap} + TTL
 * 的本地内存实现。
 *
 * <p>接口方法对标 common-cache 的 {@code Cache} 最小子集，仅覆盖 safe 模块实际使用的操作。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SafeCache<K, V> {

  /**
   * 获取缓存值（如果存在）
   *
   * @param key 缓存键
   * @return 缓存值，不存在则返回 null
   */
  V getIfPresent(K key);

  /**
   * 放入缓存
   *
   * @param key 键
   * @param value 值
   */
  void put(K key, V value);

  /**
   * 如果键不存在则放入缓存。
   *
   * @param key 键
   * @param value 值
   * @return 写入前已存在的值；写入成功返回 null
   */
  V putIfAbsent(K key, V value);

  /**
   * 使键失效
   *
   * @param key 键
   */
  void invalidate(K key);

  /**
   * 使所有键失效
   */
  void invalidateAll();

  /**
   * 获取缓存条目估计数
   *
   * @return 条目数估计值
   */
  long estimatedSize();

  /**
   * 执行缓存维护（清理过期条目）
   */
  void cleanUp();
}
