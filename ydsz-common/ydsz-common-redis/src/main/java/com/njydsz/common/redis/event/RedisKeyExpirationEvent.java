package com.njydsz.common.redis.event;

import java.time.Instant;

/**
 * Redis Key 过期事件
 *
 * <p>封装了 Redis Key 过期时的事件信息，传递给 {@link RedisKeyExpireListener} 标注的方法。
 *
 * @param expiredKey 过期的原始 key（含 Redis keyPrefix）
 * @param businessKey 去除业务前缀后的 key（如果可解析）
 * @param occurredAt 事件发生时间戳
 * @author ydsz-team
 * @since 1.0.0
 */
public record RedisKeyExpirationEvent(String expiredKey, String businessKey, Instant occurredAt) {

  /**
   * 构造事件（使用当前时间）
   *
   * @param expiredKey 过期的原始 key
   * @param businessKey 业务 key
   */
  public RedisKeyExpirationEvent(String expiredKey, String businessKey) {
    this(expiredKey, businessKey, Instant.now());
  }

  /**
   * 仅使用原始 key 构造事件（businessKey 相同）
   *
   * @param expiredKey 过期的原始 key
   */
  public RedisKeyExpirationEvent(String expiredKey) {
    this(expiredKey, expiredKey, Instant.now());
  }

  @Override
  public String toString() {
    return String.format(
        "RedisKeyExpirationEvent{key='%s', occurredAt=%s}", expiredKey, occurredAt);
  }
}
