package com.njydsz.common.safe.ratelimit.cluster;

import java.util.List;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 集群限流器接口
 *
 * <p>集群限流通过共享存储（Redis、Tair 等）实现跨节点的全局限流，
 * 解决本地限流在集群环境下「单实例精确、整体不精确」的问题。
 *
 * <p>典型实现：
 * <ul>
 *   <li>Redis 令牌桶（基于 Lua 脚本原子操作）</li>
 *   <li>Redis 滑动窗口（基于 ZSET）</li>
 *   <li>Redis 计数器（基于 INCR + EXPIRE）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ClusterRateLimiter {

    /**
     * 集群限流决策
     */
    RateLimitDecision tryAcquire(RateLimitRule rule, RateLimitContext context);

    /**
     * 获取支持的模式
     */
    RateLimitMode getMode();

    /**
     * 批量尝试（批量接口可一次判定多个请求）
     */
    default List<RateLimitDecision> tryAcquireBatch(RateLimitRule rule, RateLimitContext context, int count) {
        throw new UnsupportedOperationException("batch acquire not supported");
    }
}
