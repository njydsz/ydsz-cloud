package com.remisoft.common.safe.ratelimit.algorithm;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.remisoft.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.remisoft.common.safe.ratelimit.enums.RateLimitResult;
import com.remisoft.common.safe.ratelimit.model.RateLimitContext;
import com.remisoft.common.safe.ratelimit.model.RateLimitDecision;
import com.remisoft.common.safe.ratelimit.model.RateLimitRule;

import lombok.extern.slf4j.Slf4j;

/**
 * 并发数限流器（信号量）
 *
 * <p>基于 {@link Semaphore} 实现的并发数限流，适合资源隔离、线程池限流等场景。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * RateLimitDecision decision = limiter.tryAcquire(context);
 * if (decision.isPass()) {
 *     try {
 *         // 执行业务逻辑
 *     } finally {
 *         limiter.release(context);  // 必须释放
 *     }
 * } else {
 *     // 拒绝
 * }
 * }</pre>
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>限制同时执行的慢查询数（如 DB 慢查询上限 50）</li>
 *   <li>限制并发下载任务数</li>
 *   <li>第三方 API 调用并发隔离</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class ConcurrencyLimiter implements RateLimiter {

    private final RateLimitRule rule;

    /** 资源 → 信号量 */
    private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    /** 资源 → 实际并发计数（监控用） */
    private final ConcurrentHashMap<String, AtomicInteger> activeCounts = new ConcurrentHashMap<>();

    public ConcurrencyLimiter(RateLimitRule rule) {
        rule.validate();
        this.rule = rule;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitContext context) {
        String key = context.getResource();
        Semaphore semaphore = semaphores.computeIfAbsent(key,
                k -> new Semaphore((int) rule.getThreshold()));
        AtomicInteger active = activeCounts.computeIfAbsent(key, k -> new AtomicInteger(0));

        boolean acquired;
        if (rule.getQueueTimeout() != null && !rule.getQueueTimeout().isZero()) {
            try {
                acquired = semaphore.tryAcquire(rule.getQueueTimeout().toMillis(),
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                acquired = false;
            }
        } else {
            acquired = semaphore.tryAcquire();
        }

        if (acquired) {
            active.incrementAndGet();
            return RateLimitDecision.builder()
                    .result(RateLimitResult.PASS)
                    .remaining(rule.getThreshold() - active.get())
                    .threshold(rule.getThreshold())
                    .timestamp(Instant.now())
                    .reason("concurrency permit acquired")
                    .build();
        } else {
            return RateLimitDecision.builder()
                    .result(RateLimitResult.BLOCKED)
                    .remaining(0)
                    .threshold(rule.getThreshold())
                    .waitTimeMillis(rule.getQueueTimeout() == null ? 0
                            : rule.getQueueTimeout().toMillis())
                    .timestamp(Instant.now())
                    .reason("concurrency limit reached")
                    .build();
        }
    }

    @Override
    public void release(RateLimitContext context) {
        String key = context.getResource();
        Semaphore semaphore = semaphores.get(key);
        AtomicInteger active = activeCounts.get(key);
        if (semaphore != null && semaphore.availablePermits() < rule.getThreshold()) {
            semaphore.release();
        }
        if (active != null) {
            active.decrementAndGet();
        }
    }

    @Override
    public RateLimitAlgorithm getAlgorithm() {
        return RateLimitAlgorithm.CONCURRENCY;
    }

    @Override
    public RateLimitRule getRule() {
        return rule;
    }

    @Override
    public void reset() {
        semaphores.clear();
        activeCounts.clear();
    }

    /**
     * 获取当前活跃并发数（监控用）
     */
    public int getActiveCount(String resource) {
        AtomicInteger active = activeCounts.get(resource);
        return active == null ? 0 : active.get();
    }
}
