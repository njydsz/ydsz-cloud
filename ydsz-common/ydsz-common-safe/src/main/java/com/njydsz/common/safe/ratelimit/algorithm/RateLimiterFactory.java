package com.njydsz.common.safe.ratelimit.algorithm;

import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

import lombok.extern.slf4j.Slf4j;

/**
 * 限流器工厂
 *
 * <p>根据 {@link RateLimitAlgorithm} 创建对应的 {@link RateLimiter} 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
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

        // 废弃算法降级警告
        if (algorithm != RateLimitAlgorithm.TOKEN_BUCKET) {
            log.warn("限流算法 {} 已废弃，建议迁移至 token-bucket", algorithm);
        }

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
