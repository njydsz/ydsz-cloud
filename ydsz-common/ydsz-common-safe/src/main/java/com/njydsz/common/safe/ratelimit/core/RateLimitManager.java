package com.njydsz.common.safe.ratelimit.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.njydsz.common.safe.ratelimit.algorithm.RateLimiter;
import com.njydsz.common.safe.ratelimit.cluster.ClusterRateLimiter;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;
import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;
import com.njydsz.common.safe.ratelimit.properties.RateLimitProperties;
import com.njydsz.common.safe.ratelimit.spi.RateLimitRuleProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 限流管理器
 *
 * <p>统一入口：根据规则模式（LOCAL / CLUSTER / ADAPTIVE）分发到不同的限流器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RateLimitManager {

    private final RateLimitRuleProvider ruleProvider;
    private final RateLimitRuleCache ruleCache;
    private final RateLimitProperties properties;
    private final ClusterRateLimiter clusterLimiter;

    /** 决策监听器（用于埋点） */
    private final List<DecisionListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 构造函数（推荐用法，由 Spring 注入 {@link ClusterRateLimiter} Bean）
     *
     * @param ruleProvider 规则提供器
     * @param properties   限流配置
     * @param clusterLimiter 集群限流器（可由 {@code RateLimitAutoConfiguration} 注入；
     *                       为 {@code null} 时集群模式将降级为 PASS/BLOCK）
     */
    public RateLimitManager(RateLimitRuleProvider ruleProvider,
                            RateLimitProperties properties,
                            ClusterRateLimiter clusterLimiter) {
        this.ruleProvider = ruleProvider;
        this.properties = properties;
        this.ruleCache = new RateLimitRuleCache(ruleProvider);
        this.clusterLimiter = clusterLimiter;
        if (clusterLimiter == null) {
            log.warn("RateLimitManager initialized without ClusterRateLimiter; CLUSTER mode will fall back to {}", properties.getFallbackOnError());
        }
    }

    /**
     * 核心限流决策入口
     */
    public RateLimitDecision decide(RateLimitContext context) {
        if (!properties.isEnabled()) {
            return passThrough(context, "ratelimit disabled");
        }
        Optional<RateLimiter> limiterOpt = ruleCache.getLimiter(context.getResource());
        if (limiterOpt.isEmpty()) {
            return passThrough(context, "no rule matched");
        }
        RateLimiter limiter = limiterOpt.get();
        RateLimitRule rule = limiter.getRule();
        if (!rule.isEnabled()) {
            return passThrough(context, "rule disabled");
        }

        RateLimitDecision decision;
        try {
            if (rule.getMode() == RateLimitMode.LOCAL) {
                decision = limiter.tryAcquire(context);
            } else if (rule.getMode() == RateLimitMode.CLUSTER) {
                if (clusterLimiter == null) {
                    // ClusterRateLimiter 未注入（如 Redis 未启用），降级为本地限流
                    log.debug("ClusterRateLimiter not available, fall back to local limiter for resource={}", context.getResource());
                    decision = limiter.tryAcquire(context);
                } else {
                    decision = clusterLimiter.tryAcquire(rule, context);
                }
            } else {
                // ADAPTIVE / HYBRID：简化处理，按 LOCAL 处理
                decision = limiter.tryAcquire(context);
            }
        } catch (Exception ex) {
            log.error("Rate limit decision failed for resource={}", context.getResource(), ex);
            decision = handleFallback(context, rule, ex);
        }
        decision.setResource(context.getResource());
        notifyListeners(decision);
        return decision;
    }

    /**
     * 放行
     */
    private RateLimitDecision passThrough(RateLimitContext context, String reason) {
        return RateLimitDecision.builder()
                .resource(context.getResource())
                .result(RateLimitResult.PASS)
                .remaining(Double.MAX_VALUE)
                .threshold(-1)
                .timestamp(Instant.now())
                .reason(reason)
                .build();
    }

    /**
     * 失败降级
     */
    private RateLimitDecision handleFallback(RateLimitContext context, RateLimitRule rule, Throwable ex) {
        if ("BLOCK".equalsIgnoreCase(properties.getFallbackOnError())) {
            return RateLimitDecision.builder()
                    .resource(context.getResource())
                    .rule(rule)
                    .result(RateLimitResult.BLOCKED)
                    .remaining(0)
                    .timestamp(Instant.now())
                    .reason("error fallback: " + ex.getMessage())
                    .build();
        }
        return passThrough(context, "error fallback: " + ex.getMessage());
    }

    /**
     * 添加决策监听器
     */
    public void addListener(DecisionListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners(RateLimitDecision decision) {
        for (DecisionListener listener : listeners) {
            try {
                listener.onDecision(decision);
            } catch (Exception ex) {
                log.warn("Decision listener failed", ex);
            }
        }
    }

    /**
     * 重新加载规则
     */
    public void reload() {
        ruleCache.reload();
    }

    /**
     * 获取规则提供器
     */
    public RateLimitRuleProvider getRuleProvider() {
        return ruleProvider;
    }

    /**
     * 获取规则缓存
     */
    public RateLimitRuleCache getRuleCache() {
        return ruleCache;
    }

    /**
     * 决策监听器（用于指标埋点/告警）
     */
    @FunctionalInterface
    public interface DecisionListener {
        void onDecision(RateLimitDecision decision);
    }
}
