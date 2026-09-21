package com.njydsz.common.lock.idempotent;

import com.njydsz.common.safe.idempotent.strategy.RedisIdempotentStrategy;

/**
 * 基于 Redis SET NX EX 的幂等策略实现（已迁移）
 *
 * <p><b>已迁移至 {@link com.njydsz.common.safe.idempotent.strategy.RedisIdempotentStrategy}。</b>
 * 本类继承自安全模块的实现，仅为向后兼容保留。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.strategy.RedisIdempotentStrategy} 替代
 */
@Deprecated
@SuppressWarnings("all")
public class RedisIdempotentStrategy extends com.njydsz.common.safe.idempotent.strategy.RedisIdempotentStrategy {

  /**
   * @see com.njydsz.common.safe.idempotent.strategy.RedisIdempotentStrategy#RedisIdempotentStrategy(org.springframework.data.redis.core.StringRedisTemplate)
   */
  public RedisIdempotentStrategy(org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
    super(redisTemplate);
  }

  /**
   * @see com.njydsz.common.safe.idempotent.strategy.RedisIdempotentStrategy#RedisIdempotentStrategy(org.springframework.data.redis.core.StringRedisTemplate, boolean)
   */
  public RedisIdempotentStrategy(org.springframework.data.redis.core.StringRedisTemplate redisTemplate, boolean failOpen) {
    super(redisTemplate, failOpen);
  }
}
