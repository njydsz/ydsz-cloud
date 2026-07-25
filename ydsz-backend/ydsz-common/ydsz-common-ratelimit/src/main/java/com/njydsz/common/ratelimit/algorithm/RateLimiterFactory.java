package com.njydsz.common.ratelimit.algorithm;

import com.njydsz.common.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.ratelimit.model.RateLimitRule;

/**
 * 限流器工厂
 *
 * <p>根据 {@link RateLimitAlgorithm} 创建对应的 {@link RateLimiter} 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class RateLimiterFactory {

    private RateLimiterFactory() {
    }

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
        RateLimitAlgorithm algorithm = rule.getAlgorithm() == null
                ? RateLimitAlgorithm.TOKEN_BUCKET
                : rule.getAlgorithm();
        switch (algorithm) {
            case COUNTER:
                return new CounterLimiter(rule);
            case SLIDING_WINDOW:
                return new SlidingWindowLimiter(rule);
            case TOKEN_BUCKET:
                return new TokenBucketLimiter(rule);
            case LEAKY_BUCKET:
                return new LeakyBucketLimiter(rule);
            case CONCURRENCY:
                return new ConcurrencyLimiter(rule);
            default:
                throw new IllegalArgumentException("unsupported algorithm: " + algorithm);
        }
    }
}
