package com.njydsz.common.safe.ratelimit.algorithm;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

import lombok.extern.slf4j.Slf4j;

/**
 * 固定窗口计数器限流器
 *
 * <p>最简单的限流算法：将时间划分为固定大小的窗口，每个窗口内独立计数。
 *
 * <p><b>算法原理：</b>
 * <ul>
 *   <li>窗口起始时间 = (now / windowMillis) * windowMillis</li>
 *   <li>当前窗口的请求计数 = AtomicLong，窗口切换时归零</li>
 *   <li>若当前计数 < 阈值：放行并 +1</li>
 *   <li>否则拒绝</li>
 * </ul>
 *
 * <p><b>优缺点：</b>
 * <ul>
 *   <li>优点：实现简单、内存占用低</li>
 *   <li>缺点：窗口边界突刺（边界处可能在 window*2 内通过 2*threshold 个请求）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 存在窗口边界突刺问题，推荐使用 {@link TokenBucketLimiter}（支持突发流量、性能更优）
 */
@Deprecated
@Slf4j
public class CounterLimiter implements RateLimiter {

    private final RateLimitRule rule;

    /** 资源 → 计数器窗口 */
    private final ConcurrentHashMap<String, CounterWindow> windows = new ConcurrentHashMap<>();

    public CounterLimiter(RateLimitRule rule) {
        rule.validate();
        this.rule = rule;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitContext context) {
        String key = context.getResource();
        CounterWindow window = windows.computeIfAbsent(key, k -> new CounterWindow(rule));
        return window.tryAcquire();
    }

    @Override
    public RateLimitAlgorithm getAlgorithm() {
        return RateLimitAlgorithm.COUNTER;
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
     * 固定窗口内部实现
     */
    private static class CounterWindow {
        private final long windowMillis;
        private final double threshold;
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong windowStart = new AtomicLong(0);

        CounterWindow(RateLimitRule rule) {
            this.windowMillis = rule.getWindow().toMillis();
            this.threshold = rule.getThreshold();
            this.windowStart.set(currentWindowStart());
        }

        private long currentWindowStart() {
            return (System.currentTimeMillis() / windowMillis) * windowMillis;
        }

        synchronized RateLimitDecision tryAcquire() {
            long now = System.currentTimeMillis();
            long currentStart = currentWindowStart();
            if (now - currentStart >= windowMillis || windowStart.get() != currentStart) {
                // 窗口切换，归零
                windowStart.set(currentStart);
                count.set(0);
            }
            long current = count.get();
            if (current < threshold) {
                count.incrementAndGet();
                return RateLimitDecision.builder()
                        .result(RateLimitResult.PASS)
                        .remaining(threshold - current - 1)
                        .threshold(threshold)
                        .timestamp(Instant.now())
                        .reason("counter pass")
                        .build();
            } else {
                long waitMs = windowMillis - (now - currentStart);
                return RateLimitDecision.builder()
                        .result(RateLimitResult.BLOCKED)
                        .remaining(0)
                        .threshold(threshold)
                        .waitTimeMillis(Math.max(0, waitMs))
                        .timestamp(Instant.now())
                        .reason("counter threshold exceeded")
                        .build();
            }
        }
    }
}
