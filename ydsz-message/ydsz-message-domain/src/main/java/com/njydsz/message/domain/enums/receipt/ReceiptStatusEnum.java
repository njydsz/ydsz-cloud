package com.njydsz.message.domain.enums.receipt;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 消息回执状态枚举。
 *
 * <p>对应 SQL {@code ydsz_msg_log.receipt_status} 的 CHECK 约束取值。 实现 {@link BaseStatusEnum} 契约，提供
 * {@link #canTransitTo} 状态流转校验。
 *
 * <ul>
 *   <li>{@link #NONE} - 无回执（发送成功后初始态）
 *   <li>{@link #DELIVERED} - 已送达（服务商确认投递到终端）
 *   <li>{@link #READ} - 已读（用户已查看）
 *   <li>{@link #CLICKED} - 已点击（用户点击了消息中的链接）
 *   <li>{@link #FAILED} - 投递失败（服务商侧投递失败）
 *   <li>{@link #TIMEOUT} - 回执超时（P2-9: 超过阈值仍未收到回执，由 {@code ReceiptPuller} 标记）
 * </ul>
 *
 * <p><b>状态流转规则：</b>
 *
 * <ul>
 *   <li>NONE → DELIVERED / FAILED / TIMEOUT
 *   <li>DELIVERED → READ / TIMEOUT / FAILED
 *   <li>READ → CLICKED
 *   <li>CLICKED / FAILED / TIMEOUT 为终态，不可再流转
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum ReceiptStatusEnum implements BaseStatusEnum<ReceiptStatusEnum> {

  /** 无回执 */
  NONE,
  /** 已送达 */
  DELIVERED,
  /** 已读 */
  READ,
  /** 已点击 */
  CLICKED,
  /** 投递失败 */
  FAILED,
  /** 回执超时（P2-9） */
  TIMEOUT;

  /**
   * {@inheritDoc}
   *
   * <p>CLICKED、FAILED、TIMEOUT 为终态，不可再流转。
   */
  @Override
  public boolean isTerminal() {
    return this == CLICKED || this == FAILED || this == TIMEOUT;
  }

  /**
   * {@inheritDoc}
   *
   * <p>流转规则：
   *
   * <ul>
   *   <li>NONE → DELIVERED / FAILED / TIMEOUT
   *   <li>DELIVERED → READ / TIMEOUT / FAILED
   *   <li>READ → CLICKED
   *   <li>CLICKED / FAILED / TIMEOUT 为终态，不可再流转
   * </ul>
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(ReceiptStatusEnum target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case NONE -> target == DELIVERED || target == FAILED || target == TIMEOUT;
      case DELIVERED -> target == READ || target == TIMEOUT || target == FAILED;
      case READ -> target == CLICKED;
      case CLICKED, FAILED, TIMEOUT -> false;
    };
  }
}
