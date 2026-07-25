package com.njydsz.common.ratelimit.algorithm;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.common.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.ratelimit.enums.RateLimitResult;
import com.njydsz.common.ratelimit.model.RateLimitContext;
import com.njydsz.common.ratelimit.model.RateLimitDecision;
import com.njydsz.common.ratelimit.model.RateLimitRule;

import lombok.extern.slf4j.Slf4j;

/**
 * 漏桶限流器
 *
 * <p>基于「恒定流出速率」实现的漏桶算法。
 *
 * <p><b>算法原理：</b>
 * <ul>
 *   <li>请求进入桶中，桶有固定容量</li>
 *   <li>桶底以恒定速率漏水（处理请求）</li>
 *   <li>桶满则拒绝新请求（提供背压机制）</li>
 * </ul>
 *
 * <p><b>与令牌桶的区别：</b>漏桶强制恒定速率输出（流量整形），不能像令牌桶那样支持突发流量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class LeakyBucketLimiter implements RateLimiter {

    private final RateLimitRule rule;

    /** 资源 → 漏桶 */
    private final ConcurrentHashMap<String, LeakyBucket> buckets = new ConcurrentHashMap<>();

    public LeakyBucketLimiter(RateLimitRule rule) {
        rule.validate();
        this.rule = rule;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitContext context) {
        String key = context.getResource();
        LeakyBucket bucket = buckets.computeIfAbsent(key, k -> new LeakyBucket(rule));
        return bucket.tryAcquire();
    }

    @Override
    public RateLimitAlgorithm getAlgorithm() {
        return RateLimitAlgorithm.LEAKY_BUCKET;
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
     * 漏桶内部实现
     */
    private static class LeakyBucket {
        /** 桶容量 */
        private final long capacity;
        /** 漏水速率（每秒处理数） */
        private final double rate;
        /** 当前桶内水位（纳秒精度） */
        private final AtomicLong waterNanos = new AtomicLong(0);
        /** 上次漏水时间（纳秒） */
        private final AtomicLong lastLeakNanos = new AtomicLong(System.nanoTime());

        LeakyBucket(RateLimitRule rule) {
            this.capacity = rule.getBurstCapacity();
            this.rate = rule.getThreshold();
        }

        synchronized RateLimitDecision tryAcquire() {
            long now = System.nanoTime();
            long last = lastLeakNanos.get();
            long elapsedNanos = now - last;

            // 漏掉的水量（纳秒）
            long leakNanos = (long) (elapsedNanos * rate);
            long current = waterNanos.addAndGet(-leakNanos);
            if (current < 0) {
                waterNanos.set(0);
                current = 0;
            }
            lastLeakNanos.set(now);

            long capacityNanos = capacity * 1_000_000_000L;
            if (current + 1_000_000_000L <= capacityNanos) {
                // 加入 1 滴水
                waterNanos.addAndGet(1_000_000_000L);
                return RateLimitDecision.builder()
                        .result(RateLimitResult.PASS)
                        .remaining((capacityNanos - current - 1_000_000_000L) / 1_000_000_000.0)
                        .threshold(capacity)
                        .timestamp(Instant.now())
                        .reason("leaky bucket pass")
                        .build();
            } else {
                // 计算需要等待多久才能加入
                long needNanos = (current + 1_000_000_000L) - capacityNanos;
                long waitMs = (long) Math.ceil(needNanos / rate / 1_000_000.0);
                return RateLimitDecision.builder()
                        .result(RateLimitResult.BLOCKED)
                        .remaining(0)
                        .threshold(capacity)
                        .waitTimeMillis(waitMs)
                        .timestamp(Instant.now())
                        .reason("leaky bucket full")
                        .build();
            }
        }
    }
}
