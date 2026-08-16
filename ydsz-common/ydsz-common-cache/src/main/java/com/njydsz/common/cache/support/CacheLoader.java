package com.njydsz.common.cache.support;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 缓存加载器接口 - 支持自动加载和批量加载
 *
 * <p>核心功能：
 *
 * <ul>
 *   <li>单键加载：缓存未命中时自动调用 load() 加载数据
 *   <li>批量加载：支持 getAll() 批量加载，减少数据库查询次数
 *   <li>异步加载：支持 loadAsync() 异步加载，不阻塞调用线程
 *   <li>批量异步加载：支持 loadAllAsync() 批量异步加载
 * </ul>
 *
 * <p>与 Function 的区别：
 *
 * <ul>
 *   <li>CacheLoader 支持批量和异步操作，更适合缓存场景
 *   <li>支持加载时间统计，自动记录加载耗时
 *   <li>提供默认实现，简化使用
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 创建 CacheLoader
 * CacheLoader<String, User> loader = new CacheLoader<>() {
 *     @Override
 *     public User load(String key) {
 *         return userDao.findById(key);
 *     }
 *
 *     @Override
 *     public Map<String, User> loadAll(Iterable<? extends String> keys) {
 *         return userDao.findAllByIds(keys);
 *     }
 * };
 *
 * // 或使用 Lambda
 * CacheLoader<String, User> loader = CacheLoader.from(key -> userDao.findById(key));
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CacheLoader<K, V> {

  /**
   * 加载单个缓存项
   *
   * @param key 缓存键
   * @return 加载的值，如果返回 null 则不放入缓存
   * @throws Exception 加载异常
   */
  V load(K key) throws Exception;

  /**
   * 批量加载缓存项
   *
   * <p>默认实现：逐个调用 load()，子类可覆盖以优化批量查询
   *
   * @param keys 缓存键集合
   * @return 键值对映射
   * @throws Exception 加载异常
   */
  default Map<K, V> loadAll(Iterable<? extends K> keys) throws Exception {
    Map<K, V> result = new HashMap<>();
    for (K key : keys) {
      V value = load(key);
      if (value != null) {
        result.put(key, value);
      }
    }
    return result;
  }

  /**
   * 异步加载单个缓存项
   *
   * <p>默认实现：同步调用 load() 并包装为 CompletableFuture
   *
   * @param key 缓存键
   * @return 异步完成的值
   */
  default CompletableFuture<V> loadAsync(K key) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return load(key);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  /**
   * 批量异步加载缓存项
   *
   * <p>默认实现：同步调用 loadAll() 并包装为 CompletableFuture
   *
   * @param keys 缓存键集合
   * @return 异步完成的键值对映射
   */
  default CompletableFuture<Map<K, V>> loadAllAsync(Iterable<? extends K> keys) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return loadAll(keys);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  /**
   * 从 Function 创建 CacheLoader
   *
   * @param loader 加载函数
   * @param <K> 键类型
   * @param <V> 值类型
   * @return CacheLoader 实例
   */
  static <K, V> CacheLoader<K, V> from(Function<K, V> loader) {
    return new CacheLoader<>() {
      @Override
      public V load(K key) {
        return loader.apply(key);
      }
    };
  }
}
