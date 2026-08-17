package com.njydsz.workflow.domain.enums;

/**
 * 会签类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum FlowPerformType {

  /** 或签：任一办理人通过即推进 */
  OR,
  /** 顺序会签：按办理人顺序逐一处理，全部通过才推进 */
  SEQUENTIAL,
  /** 并行会签：所有办理人全部通过才推进 */
  PARALLEL,
  /**
   * GAP-P2-10: FOREACH 并行循环 — 每个集合元素对应一条独立 task，全部完成才推进
   *
   * <p>与 {@link #PARALLEL}（会签）的区别：会签是 1 task + N user 共享审批意见； FOREACH_PARALLEL 是 N 条独立 task，每条 task
   * 独立完成，全部完成才推进。
   */
  FOREACH_PARALLEL
}
