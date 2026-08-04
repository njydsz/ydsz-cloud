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

    /**
     * 规则变更类型。
     *
     * <p>标记一次规则变更的增删改语义，供监听器按需增量刷新本地限流规则缓存，避免全量重建。
     */
    enum ChangeType {
        /** 规则新增。 */
        ADDED,
        /** 规则更新。 */
        UPDATED,
        /** 规则移除。 */
        REMOVED
    }
}
