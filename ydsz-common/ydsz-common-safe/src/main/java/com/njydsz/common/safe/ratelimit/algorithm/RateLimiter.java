package com.njydsz.common.safe.ratelimit.algorithm;

import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 限流器接口
 *
 * <p>所有限流算法（计数器、滑动窗口、令牌桶、漏桶、并发数等）的统一抽象。
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * RateLimiter limiter = new TokenBucketLimiter(rule);
 * RateLimitContext ctx = RateLimitContext.builder().resource("user.login").build();
 * RateLimitDecision decision = limiter.tryAcquire(ctx);
 * if (decision.isBlocked()) {
 *     // 限流拒绝
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @author ydsz-team
 * @since 26.09.01
 */
public interface RateLimiter {

  /**
   * 尝试获取一个令牌/许可
   *
   * <p>同步阻塞版本，立即返回决策结果（不等待）。
   *
   * @param context 限流上下文
   * @return 限流决策
   */
  RateLimitDecision tryAcquire(RateLimitContext context);

  /**
   * 尝试获取一个令牌/许可（带超时）
   *
   * <p>如果设置了排队等待（{@link RateLimitRule#getQueueTimeout()} > 0）， 则阻塞等待直到获取或超时。
   *
   * @param context 限流上下文
   * @return 限流决策
   */
  default RateLimitDecision tryAcquireWithTimeout(RateLimitContext context) {
    return tryAcquire(context);
  }

  /**
   * 获取支持的算法。
   *
   * @return 限流算法类型
   */
  RateLimitAlgorithm getAlgorithm();

  /**
   * 获取当前规则。
   *
   * @return 限流规则
   */
  RateLimitRule getRule();

  /**
   * 重置限流器状态。
   *
   * <p>清空窗口/计数/令牌等内部状态，用于规则变更或运维重置。
   */
  void reset();

  /**
   * 释放一个令牌/许可（用于并发数限流的 finally 块）。
   *
   * @param context 限流上下文
   */
  default void release(RateLimitContext context) {
    // 默认无操作，仅并发数限流器需要实现
  }
}
