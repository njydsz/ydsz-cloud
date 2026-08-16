package com.njydsz.common.redis.enums;

/**
 * Redis 操作异常
 *
 * <p>当 {@link FailOpenPolicy#FAIL_THROW} 策略生效时，Redis 操作失败会抛出此异常。 封装了操作的 key、操作名称和原始异常，便于上层统一处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RedisOperationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 操作的 Redis key（可为 null） */
  private final String key;

  /** 操作名称（如 "GET", "SET", "HGET"） */
  private final String operation;

  /**
   * 构造 Redis 操作异常
   *
   * @param key 操作的 key
   * @param operation 操作名称
   * @param cause 原始异常
   */
  public RedisOperationException(String key, String operation, Throwable cause) {
    super(
        String.format(
            "Redis 操作失败 | operation=%s | key=%s | cause=%s", operation, key, cause.getMessage()),
        cause);
    this.key = key;
    this.operation = operation;
  }

  /**
   * 获取操作的 key
   *
   * @return key（可为 null）
   */
  public String getKey() {
    return key;
  }

  /**
   * 获取操作名称
   *
   * @return 操作名称
   */
  public String getOperation() {
    return operation;
  }
}
