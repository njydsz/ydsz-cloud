package com.njydsz.common.safe.idempotent;

import java.time.Duration;

/**
 * 幂等键存储抽象。
 *
 * <p>提供分布式与本地两种实现，解耦幂等拦截器与具体存储技术。
 *
 * <p>实现类：
 *
 * <ul>
 *   <li>{@code RedisIdempotentStore} - 基于 Redis SETNX 的分布式实现（由 redis 模块提供）
 *   <li>{@code InMemoryIdempotentStore} - 基于 ConcurrentHashMap 的本地实现（单节点兜底）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface IdempotentStore {

  /**
   * 尝试获取幂等锁。
   *
   * <p>若键不存在则原子性写入并设置过期时间，返回 true； 若键已存在则返回 false。
   *
   * @param key 幂等键
   * @param expire 过期时间
   * @return true=获取成功（首次请求），false=重复请求
   */
  boolean tryAcquire(String key, Duration expire);

  /**
   * 释放幂等锁。
   *
   * <p>主动移除幂等键，允许后续请求重新获取。 注意：正常情况应由过期时间自动释放，仅在业务失败需重试时手动调用。
   *
   * @param key 幂等键
   */
  void release(String key);
}
