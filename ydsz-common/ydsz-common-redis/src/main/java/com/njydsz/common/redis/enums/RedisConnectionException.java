package com.njydsz.common.redis.enums;

/**
 * Redis 连接异常
 *
 * <p>当 Redis 连接失败、超时等基础设施故障时抛出此异常。 该异常触发自动重试机制，属于可恢复异常。
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>RedisConnectionFailureException（连接断开）
 *   <li>QueryTimeoutException（查询超时）
 *   <li>网络抖动导致的暂时性故障
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RedisConnectionException extends RedisOperationException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造 Redis 连接异常
   *
   * @param key 操作的 key
   * @param operation 操作名称
   * @param cause 原始异常
   */
  public RedisConnectionException(String key, String operation, Throwable cause) {
    super(key, operation, cause);
  }

  /**
   * 构造 Redis 连接异常（无 key 场景）
   *
   * @param operation 操作名称
   * @param cause 原始异常
   */
  public RedisConnectionException(String operation, Throwable cause) {
    super(null, operation, cause);
  }
}
