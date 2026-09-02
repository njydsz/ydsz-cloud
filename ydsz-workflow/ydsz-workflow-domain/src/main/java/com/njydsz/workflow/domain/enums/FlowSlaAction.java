package com.njydsz.workflow.domain.enums;

/**
 * GAP-P1: SLA 超时处理动作
 *
 * <p>当审批任务超过 {@code slaConfig.timeoutMinutes} 后触发的自动处理策略，支持 SLA 超时自动化能力。
 *
 * <p>P1-3 闭环语义：每个动作都必须有明确的终态，不允许"标记超时但流程卡死"。
 *
 * <ul>
 *   <li>{@link #REMIND} — 中间态：发送催办通知（站内信/邮件/企微），任务保持活跃
 *   <li>{@link #NOTIFY} — 最终态：通知管理员/升级人介入，任务保持活跃（等人工处理）
 *   <li>{@link #ESCALATE} — 最终态：升级到上级（自动转办给 escalateUserId），任务保持活跃
 *   <li>{@link #AUTO_PASS} — 最终态：自动通过（超时自动审批通过），流程推进到下一节点
 *   <li>{@link #AUTO_REJECT}— 最终态：自动驳回（超时自动驳回），流程终止
 * </ul>
 *
 * <p>REMIND 作为中间态，达到 {@code maxYdsznders} 后会切换到 {@code finalAction} （默认 NOTIFY）。这样保证 SLA
 * 链路始终闭环：要么任务被人处理，要么被系统自动推进， 要么持续通知管理员介入，绝不出现"标记 TIMEOUT 后流程卡死"的情况。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum FlowSlaAction {

  /** 中间态：发送催办通知（站内信/邮件/企微），任务保持活跃 */
  REMIND,

  /** 最终态：通知管理员/升级人介入，任务保持活跃（等人工处理） */
  NOTIFY,

  /** 最终态：升级到上级（自动转办给 escalateUserId），任务保持活跃 */
  ESCALATE,

  /** 最终态：自动通过（超时自动审批通过），流程推进到下一节点 */
  AUTO_PASS,

  /** 最终态：自动驳回（超时自动驳回），流程终止 */
  AUTO_REJECT
}
