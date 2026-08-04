package com.remisoft.common.cache.support;

import java.util.Map;

/**
 * 缓存写入器接口 - 支持写穿透和写回策略
 *
 * <p>核心功能：
 *
 * <ul>
 *   <li>写穿透（Write-Through）：数据同时写入缓存和后端存储，保证一致性
 *   <li>写回（Write-Behind）：数据先写入缓存，异步批量写入后端存储，提升性能
 *   <li>批量写入：支持批量写入操作，减少数据库压力
 * </ul>
 *
 * <p>写穿透模式：
 *
 * <ol>
 *   <li>应用调用 put(key, value)
 *   <li>缓存写入器同步写入后端存储
 *   <li>写入成功后更新缓存
 * </ol>
 *
 * <p>写回模式：
 *
 * <ol>
 *   <li>应用调用 put(key, value)
 *   <li>仅更新缓存，标记为脏数据
 *   <li>后台线程异步批量写入后端存储
 * </ol>
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>数据库缓存：写穿透保证数据一致性
 *   <li>配置中心：写回模式减少数据库写入压力
 *   <li>会话管理：异步写回提升响应速度
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 写穿透模式
 * CacheWriter<String, User> writer = new CacheWriter<>() {
 *     public void write(String key, User user) {
 *         userDao.save(user);
 *     }
 *     public void delete(String key, User user) {
 *         userDao.delete(key);
 *     }
 * };
 *
 * // 写回模式（异步批量写入）
 * CacheWriter<String, User> asyncWriter = CacheWriter.async(
 *     writer,
 *     100,           // 批量大小
 *     5,             // 刷新间隔（秒）
 *     executor       // 线程池
 * );
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author remi-team
 * @since 1.0.0
 * 
 */
public interface CacheWriter<K, V> {

  /**
   * 写入单个缓存项到后端存储
   *
   * @param key 缓存键
   * @param value 缓存值
   */
  void write(K key, V value);

  /**
   * 批量写入缓存项到后端存储
   *
   * @param entries 缓存项映射
   */
  default void writeAll(Map<K, V> entries) {
    for (Map.Entry<K, V> entry : entries.entrySet()) {
      write(entry.getKey(), entry.getValue());
    }
  }

  /**
   * 从后端存储删除单个缓存项
   *
   * @param key 缓存键
   * @param value 缓存值
   */
  void delete(K key, V value);

  /**
   * 批量删除缓存项从后端存储
   *
   * @param keys 缓存键集合
   */
  default void deleteAll(Iterable<? extends K> keys) {
    for (K key : keys) {
      delete(key, null);
    }
  }
}
