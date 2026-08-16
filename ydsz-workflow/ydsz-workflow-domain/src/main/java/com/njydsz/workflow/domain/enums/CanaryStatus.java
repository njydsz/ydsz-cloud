package com.njydsz.workflow.domain.enums;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 金丝雀（灰度）状态枚举
 *
 * <p>定义流程定义灰度发布生命周期中的状态流转，对标 Argo Rollouts 的 Rollout 状态机。 状态在 {@code ydsz_flow_canary.status}
 * 字段中持久化，由 {@code FlowCanaryService} 管理。 实现 {@link BaseStatusEnum} 契约，提供 {@link #canTransitTo}
 * 状态流转校验。
 *
 * <p><b>状态流转图：</b>
 *
 * <pre>
 * NONE ──(publish)──→ CANARYING ──(promote)──→ PROMOTED
 *                         │
 *                      (rollback)
 *                         ↓
 *                     ROLLED_BACK
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum CanaryStatus implements BaseStatusEnum<CanaryStatus> {

  /** 未开启 */
  NONE,

  /** 灰度中 */
  CANARYING,

  /** 已全量 */
  PROMOTED,

  /** 已回滚 */
  ROLLED_BACK;

  /**
   * {@inheritDoc}
   *
   * <p>PROMOTED 和 ROLLED_BACK 为终态，不可再流转。
   */
  @Override
  public boolean isTerminal() {
    return this == PROMOTED || this == ROLLED_BACK;
  }

  /**
   * {@inheritDoc}
   *
   * <p>流转规则：
   *
   * <ul>
   *   <li>NONE → CANARYING（开启灰度）
   *   <li>CANARYING → PROMOTED（全量发布）
   *   <li>CANARYING → ROLLED_BACK（回滚）
   *   <li>PROMOTED / ROLLED_BACK 为终态，不可再流转
   * </ul>
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(CanaryStatus target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case NONE -> target == CANARYING;
      case CANARYING -> target == PROMOTED || target == ROLLED_BACK;
      case PROMOTED, ROLLED_BACK -> false;
    };
  }
}
