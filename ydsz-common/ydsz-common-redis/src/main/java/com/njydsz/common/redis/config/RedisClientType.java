package com.njydsz.common.redis.config;

/**
 * Redis 客户端类型枚举
 *
 * <p>支持的 Redis 客户端实现：
 *
 * <ul>
 *   <li>JEDIS - 同步阻塞客户端，适合传统连接池模式
 *   <li>LETTUCE - 异步非阻塞客户端，基于 Netty，支持响应式编程
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RedisClientType {

  /**
   * Jedis 客户端
   *
   * <p>同步阻塞模型，每个连接独占一个线程，适合传统应用
   */
  JEDIS,

  /**
   * Lettuce 客户端
   *
   * <p>异步非阻塞模型，基于 Netty 实现，连接可共享，支持响应式编程
   */
  LETTUCE
}
