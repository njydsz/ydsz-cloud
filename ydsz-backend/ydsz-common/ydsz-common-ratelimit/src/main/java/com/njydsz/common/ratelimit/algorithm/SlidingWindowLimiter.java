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
 * 滑动窗口限流器
 *
 * <p>基于「分桶 + 加权计数」实现的高精度滑动窗口算法（Sentinel 风格）。
 *
 * <p><b>算法原理：</b>
 * <ul>
 *   <li>将一个统计窗口（如 1 秒）切分为 N 个等长子桶（如 10 个 100ms 桶）</li>
 *   <li>当前时间落入最新子桶，旧子桶过期后从总和中减去</li>
 *   <li>总请求数 = 所有未过期子桶的请求数之和</li>
 *   <li>引入「上一个窗口的尾段」权重，处理跨窗口请求：prevWindowCount * (1 - currentBucketProgress) + currWindowCount
 * </ul>
 *
 * <p><b>线程安全：</b>每个子桶使用 {@link LongAdder}，高并发性能优异。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SlidingWindowLimiter implements RateLimiter {

    /** 子桶数量（10 个子桶对应 100ms 一个，1 秒窗口） */
    private static final int BUCKET_COUNT = 10;

    private final RateLimitRule rule;

    /** 资源 → 窗口 */
    private final ConcurrentHashMap<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(RateLimitRule rule) {
        rule.validate();
        this.rule = rule;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitContext context) {
        String key = context.getResource();
        SlidingWindow window = windows.computeIfAbsent(key, k -> new SlidingWindow(rule));
        return window.tryAcquire();
    }

    @Override
    public RateLimitAlgorithm getAlgorithm() {
        return RateLimitAlgorithm.SLIDING_WINDOW;
    }

    @Override
    public RateLimitRule getRule() {
        return rule;
    }

    @Override
    public void reset() {
        windows.clear();
    }

    /**
     * 滑动窗口内部实现
     */
    private static class SlidingWindow {
        /** 窗口大小（毫秒） */
        private final long windowMillis;
        /** 子桶大小（毫秒） */
        private final long bucketMillis;
        /** 阈值（窗口内允许的请求数） */
        private final double threshold;
        /** 子桶数组 */
        private final LongAdder[] buckets;
        /** 上次清理时间（毫秒） */
        private final AtomicLong lastUpdateMillis = new AtomicLong(System.currentTimeMillis());

        SlidingWindow(RateLimitRule rule) {
            this.windowMillis = rule.getWindow().toMillis();
            this.bucketMillis = Math.max(1, windowMillis / BUCKET_COUNT);
            this.threshold = rule.getThreshold();
            this.buckets = new LongAdder[BUCKET_COUNT];
            for (int i = 0; i < BUCKET_COUNT; i++) {
                this.buckets[i] = new LongAdder();
            }
        }

        synchronized RateLimitDecision tryAcquire() {
            long now = System.currentTimeMillis();
            // 推进窗口，清理过期子桶
            advance(now);

            // 计算当前桶索引
            int currentIndex = (int) ((now / bucketMillis) % BUCKET_COUNT);

            // 加权计数：当前桶计数 + 上一窗口尾段计数
            long currentCount = countBuckets();
            int prevIndex = (currentIndex - 1 + BUCKET_COUNT) % BUCKET_COUNT;
            long prevCount = buckets[prevIndex].sum();

            // 计算当前桶内进度
            long bucketStart = (now / bucketMillis) * bucketMillis;
            double progress = (now - bucketStart) / (double) bucketMillis;
            // 上一桶的尾段贡献 = prevCount * (1 - progress)
            double weightedPrev = prevCount * (1.0 - progress);
            double total = currentCount - prevCount + weightedPrev;

            if (total < threshold) {
                buckets[currentIndex].increment();
                return RateLimitDecision.builder()
                        .result(RateLimitResult.PASS)
                        .remaining(threshold - total - 1)
                        .threshold(threshold)
                        .timestamp(Instant.now())
                        .reason("sliding window pass")
                        .build();
            } else {
                return RateLimitDecision.builder()
                        .result(RateLimitResult.BLOCKED)
                        .remaining(0)
                        .threshold(threshold)
                        .waitTimeMillis(bucketMillis)
                        .timestamp(Instant.now())
                        .reason("sliding window threshold exceeded")
                        .build();
            }
        }

        /**
         * 推进窗口：清理过期的子桶
         */
        private void advance(long now) {
            long last = lastUpdateMillis.get();
            if (now - last < bucketMillis) {
                return;
            }
            int currentIndex = (int) ((now / bucketMillis) % BUCKET_COUNT);
            int lastIndex = (int) ((last / bucketMillis) % BUCKET_COUNT);
            if (currentIndex == lastIndex && (now - last) < windowMillis) {
                return;
            }
            // 清空跨过整个窗口的子桶
            int steps = (int) ((now - last) / bucketMillis);
            for (int i = 1; i <= Math.min(steps, BUCKET_COUNT); i++) {
                int idx = (lastIndex + i) % BUCKET_COUNT;
                buckets[idx].reset();
            }
            lastUpdateMillis.set(now);
        }

        /**
         * 统计所有子桶的请求数
         */
        private long countBuckets() {
            long total = 0;
            for (LongAdder adder : buckets) {
                total += adder.sum();
            }
            return total;
        }
    }
}
