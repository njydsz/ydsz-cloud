package com.njydsz.common.safe.ratelimit.spi;

import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 限流规则变更监听器
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FunctionalInterface
public interface RateLimitRuleListener {

    /**
     * 规则变更回调
     */
    void onRuleChanged(RateLimitRule rule, ChangeType type);

    enum ChangeType {
        ADDED,
        UPDATED,
        REMOVED
    }
}
