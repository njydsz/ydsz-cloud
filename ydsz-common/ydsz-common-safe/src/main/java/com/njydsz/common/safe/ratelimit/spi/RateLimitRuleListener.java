package com.njydsz.common.safe.ratelimit.spi;

import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 限流规则变更监听器
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FunctionalInterface
public interface RateLimitRuleListener {

  /**
   * 规则变更回调。
   *
   * @param rule 变更的规则
   * @param type 变更类型（新增/更新/删除）
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
