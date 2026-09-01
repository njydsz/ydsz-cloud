package com.njydsz.common.safe.ratelimit.algorithm;

import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 限流器工厂
 *
 * <p>根据 {@link RateLimitAlgorithm} 创建对应的 {@link RateLimiter} 实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class RateLimiterFactory {

  private RateLimiterFactory() {}

  /**
   * 根据规则创建限流器
   *
   * @param rule 限流规则
   * @return 对应算法的限流器
   */
  public static RateLimiter create(RateLimitRule rule) {
    if (rule == null) {
      throw new IllegalArgumentException("rule cannot be null");
    }
    rule.validate();
    return new TokenBucketLimiter(rule);
  }
}
