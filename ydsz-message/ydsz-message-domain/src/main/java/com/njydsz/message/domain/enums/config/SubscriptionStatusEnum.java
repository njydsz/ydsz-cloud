package com.njydsz.message.domain.enums.config;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 订阅状态枚举。
 *
 * <p>定义用户对消息模板的订阅/退订状态。 实现 {@link BaseStatusEnum} 契约，提供 {@link #canTransitTo} 状态流转校验。
 *
 * <p><b>状态流转规则：</b>
 *
 * <ul>
 *   <li>SUBSCRIBED ⇄ UNSUBSCRIBED（可反复订阅/退订）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum SubscriptionStatusEnum implements BaseStatusEnum<SubscriptionStatusEnum> {

  /** 已订阅 */
  SUBSCRIBED,
  /** 已退订 */
  UNSUBSCRIBED;

  /**
   * {@inheritDoc}
   *
   * <p>订阅状态无终态，SUBSCRIBED 与 UNSUBSCRIBED 可相互流转。
   */
  @Override
  public boolean isTerminal() {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>流转规则：
   *
   * <ul>
   *   <li>SUBSCRIBED ↔ UNSUBSCRIBED（双向可流转）
   * </ul>
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(SubscriptionStatusEnum target) {
    if (this == target) {
      return true;
    }
    return (this == SUBSCRIBED && target == UNSUBSCRIBED)
        || (this == UNSUBSCRIBED && target == SUBSCRIBED);
  }
}
