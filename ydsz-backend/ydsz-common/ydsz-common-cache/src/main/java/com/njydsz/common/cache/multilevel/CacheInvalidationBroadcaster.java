package com.njydsz.common.cache.multilevel;

import java.util.Collection;

/**
 * 缓存失效广播器接口 — 跨节点 L1 缓存一致性
 *
 * <p>在分布式部署场景下，当一个节点更新/删除缓存时，需要通知其他节点清除本地 L1 缓存，
 * 避免各节点 L1 缓存数据不一致。
 *
 * <p>实现方案：
 *
 * <ul>
 *   <li>Redis Pub/Sub：通过 Redis 发布/订阅频道广播失效消息
 *   <li>MQ：通过消息队列广播（适用于大规模集群）
 *   <li>Noop：不广播（适用于单节点部署）
 * </ul>
 *
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CacheInvalidationBroadcaster {

  /**
   * 广播单 key 失效消息
   *
   * @param cacheName 缓存名称
   * @param key 失效的 key
   */
  void broadcastInvalidation(String cacheName, Object key);

  /**
   * 广播批量 key 失效消息
   *
   * @param cacheName 缓存名称
   * @param keys 失效的 key 集合
   */
  void broadcastInvalidationAll(String cacheName, Collection<Object> keys);

  /**
   * 广播全量失效消息（清除所有 L1 缓存）
   *
   * @param cacheName 缓存名称
   */
  void broadcastClearAll(String cacheName);

  /**
   * 注册本地失效处理器 — 当收到其他节点的广播消息时调用
   *
   * @param handler 本地失效处理器
   */
  void registerHandler(InvalidationHandler handler);

  /** 本地失效处理器接口 */
  @FunctionalInterface
  interface InvalidationHandler {

    /**
     * 处理失效消息
     *
     * @param cacheName 缓存名称
     * @param key 失效的 key（ClearAll 消息时为 null）
     * @param clearAll 是否为全量清除
     */
    void onInvalidation(String cacheName, Object key, boolean clearAll);
  }
}
