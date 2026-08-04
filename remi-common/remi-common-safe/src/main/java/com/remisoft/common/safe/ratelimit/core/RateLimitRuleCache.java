package com.remisoft.common.safe.ratelimit.core;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.remisoft.common.safe.ratelimit.algorithm.RateLimiter;
import com.remisoft.common.safe.ratelimit.algorithm.RateLimiterFactory;
import com.remisoft.common.safe.ratelimit.enums.RateLimitMode;
import com.remisoft.common.safe.ratelimit.model.RateLimitRule;
import com.remisoft.common.safe.ratelimit.spi.RateLimitRuleProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 限流规则缓存
 *
 * <p>维护「资源 → 限流器」映射，支持规则的动态热更新。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class RateLimitRuleCache {

    private final RateLimitRuleProvider provider;

    /** 资源 → 限流器实例 */
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimitRuleCache(RateLimitRuleProvider provider) {
        this.provider = provider;
        // 初始化：预热所有规则对应的限流器
        reload();
    }

    /**
     * 获取资源对应的限流器
     */
    public Optional<RateLimiter> getLimiter(String resource) {
        RateLimiter limiter = limiters.get(resource);
        if (limiter != null) {
            return Optional.of(limiter);
        }
        // 懒加载：规则提供器有则创建
        Optional<RateLimitRule> ruleOpt = provider.getRule(resource);
        if (ruleOpt.isPresent()) {
            RateLimiter newLimiter = RateLimiterFactory.create(ruleOpt.get());
            limiters.put(resource, newLimiter);
            return Optional.of(newLimiter);
        }
        return Optional.empty();
    }

    /**
     * 获取或创建限流器
     */
    public RateLimiter getOrCreate(RateLimitRule rule) {
        return limiters.computeIfAbsent(rule.getResource(), k -> RateLimiterFactory.create(rule));
    }

    /**
     * 重新加载所有规则
     */
    public void reload() {
        List<RateLimitRule> rules = provider.getAllRules();
        // 清理不存在的资源
        limiters.keySet().retainAll(rules.stream()
                .map(RateLimitRule::getResource)
                .collect(Collectors.toSet()));
        // 创建/更新
        for (RateLimitRule rule : rules) {
            if (!rule.isEnabled()) {
                limiters.remove(rule.getResource());
                continue;
            }
            if (rule.getMode() != RateLimitMode.LOCAL) {
                // 集群限流器不缓存在本地
                continue;
            }
            limiters.compute(rule.getResource(), (k, oldLimiter) -> {
                if (oldLimiter == null) {
                    return RateLimiterFactory.create(rule);
                }
                // 检查规则是否变化
                if (ruleEquals(oldLimiter.getRule(), rule)) {
                    return oldLimiter;
                }
                log.info("Rate limit rule changed for resource={}, recreating limiter", rule.getResource());
                return RateLimiterFactory.create(rule);
            });
        }
        log.info("Rate limit rule cache reloaded, active limiters={}", limiters.size());
    }

    /**
     * 清理全部
     */
    public void clear() {
        limiters.clear();
    }

    /**
     * 规则数量
     */
    public int size() {
        return limiters.size();
    }

    private static boolean ruleEquals(RateLimitRule a, RateLimitRule b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getThreshold() != b.getThreshold()) return false;
        if (a.getBurstCapacity() != b.getBurstCapacity()) return false;
        if (a.getAlgorithm() != b.getAlgorithm()) return false;
        if (a.getMode() != b.getMode()) return false;
        if (a.getWindow() == null ? b.getWindow() != null : !a.getWindow().equals(b.getWindow())) {
            return false;
        }
        return true;
    }
}
