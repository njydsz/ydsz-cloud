package com.njydsz.workflow.domain.enums;

/**
 * 流程跳转类型
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum FlowSkipType {

  /** 通过 */
  PASS,
  /** 退回（驳回，可退到任意前驱节点） */
  REJECT,
  /** 前加签：在当前节点前加审批人 */
  FORWARD,
  /** 后加签：在当前节点后加审批人 */
  BACK,
  /** 任意跳转（管理员） */
  JUMP
}
