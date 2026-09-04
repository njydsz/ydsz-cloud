package com.njydsz.message.domain.enums.batch;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 聚合批次状态枚举。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum AggregateBatchStatusEnum implements BaseStatusEnum<AggregateBatchStatusEnum> {

  /** 攒批中 */
  PENDING,
  /** 就绪待发 */
  READY,
  /** 发送中(CAS 占有中间态,防止多实例并发重复发送) */
  SENDING,
  /** 已发送 */
  SENT,
  /** 已取消 */
  CANCELLED;

  /**
   * 校验状态流转是否合法。
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(AggregateBatchStatusEnum target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case PENDING -> target == READY || target == CANCELLED;
      case READY -> target == SENDING || target == CANCELLED;
      case SENDING -> target == SENT || target == READY || target == CANCELLED;
      case SENT, CANCELLED -> false;
    };
  }

  @Override
  public boolean isTerminal() {
    return this == SENT || this == CANCELLED;
  }
}
