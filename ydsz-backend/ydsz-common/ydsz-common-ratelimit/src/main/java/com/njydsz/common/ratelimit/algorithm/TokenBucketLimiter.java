package com.njydsz.common.ratelimit.algorithm;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.njydsz.common.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.ratelimit.enums.RateLimitResult;
import com.njydsz.common.ratelimit.model.RateLimitContext;
import com.njydsz.common.ratelimit.model.RateLimitDecision;
import com.njydsz.common.ratelimit.model.RateLimitRule;

import lombok.extern.slf4j.Slf4j;

/**
 * 令牌桶限流器
 *
 * <p>基于 {@link LongAdder} 实现的令牌桶算法。
 *
 * <p><b>算法原理：</b>
 * <ul>
 *   <li>桶初始为空，容量为 burstCapacity</li>
 *   <li>以 threshold / window 速率持续往桶中放令牌</li>
 *   <li>请求到达时从桶中取一个令牌；桶空则拒绝</li>
 *   <li>支持突发流量（桶满时可瞬间通过 burstCapacity 个请求）</li>
 * </ul>
 *
 * <p><b>线程安全：</b>使用 {@link LongAdder} 保证高并发下的计数性能。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TokenBucketLimiter implements RateLimiter {

    /** 资源 → 桶 */
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private final RateLimitRule rule;

    public TokenBucketLimiter(RateLimitRule rule) {
        rule.validate();
        this.rule = rule;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitContext context) {
        String key = context.getResource();
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(rule));
        return bucket.tryAcquire();
    }

    @Override
    public RateLimitAlgorithm getAlgorithm() {
        return RateLimitAlgorithm.TOKEN_BUCKET;
    }

    @Override
    public RateLimitRule getRule() {
        return rule;
    }

    @Override
    public void reset() {
        buckets.clear();
    }

    /**
     * 令牌桶内部实现
     */
    private static class TokenBucket {
        /** 桶容量 */
        private final long capacity;
        /** 填充速率（每秒令牌数） */
        private final double refillRate;
        /** 上次填充时间（纳秒） */
        private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());
        /** 当前令牌数（精确到小数，使用双精度存储） */
        private final AtomicLong tokensNanos = new AtomicLong(0);
        /** 预热期（纳秒） */
        private final long warmupNanos;

        TokenBucket(RateLimitRule rule) {
            this.capacity = rule.getBurstCapacity();
            this.refillRate = rule.getThreshold();
            this.warmupNanos = rule.getWarmupPeriod() == null ? 0L
                    : rule.getWarmupPeriod().toNanos();
            // 启动时桶满
            this.tokensNanos.set(this.capacity * 1_000_000_000L);
        }

        /**
         * 尝试获取一个令牌
         */
        synchronized RateLimitDecision tryAcquire() {
            long now = System.nanoTime();
            long last = lastRefillNanos.get();
            long elapsedNanos = now - last;

            // 计算新令牌数（考虑预热：冷启动时慢速填充）
            double actualRate = refillRate;
            if (warmupNanos > 0) {
                // 简化实现：预热期内使用线性增长的速率
                long sinceStart = now - (last - (long) (elapsedNanos * 0.1));
                actualRate = refillRate * Math.min(1.0, sinceStart / (double) warmupNanos);
            }

            // 增加的令牌数（纳秒精度）
            long addNanos = (long) (elapsedNanos * actualRate);

            long current = tokensNanos.addAndGet(addNanos);
            long capacityNanos = capacity * 1_000_000_000L;
            if (current > capacityNanos) {
                // 桶满，截断
                tokensNanos.set(capacityNanos);
                current = capacityNanos;
            }
            lastRefillNanos.set(now);

            // 尝试取一个令牌（10亿纳秒 = 1 个令牌）
            long oneTokenNanos = 1_000_000_000L;
            if (current >= oneTokenNanos) {
                tokensNanos.addAndGet(-oneTokenNanos);
                return RateLimitDecision.builder()
                        .result(RateLimitResult.PASS)
                        .remaining(current - oneTokenNanos)
                        .threshold(capacity)
                        .timestamp(Instant.now())
                        .reason("token acquired")
                        .build();
            } else {
                // 计算需要等待的纳秒数
                long needNanos = oneTokenNanos - current;
                long waitMs = (long) Math.ceil(needNanos / actualRate / 1_000_000.0);
                return RateLimitDecision.builder()
                        .result(RateLimitResult.BLOCKED)
                        .remaining(0)
                        .threshold(capacity)
                        .waitTimeMillis(waitMs)
                        .timestamp(Instant.now())
                        .reason("token bucket empty")
                        .build();
            }
        }
    }
}
