package com.njydsz.pmis.workflow.enums;

/**
 * 会签类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FlowPerformType {

    /** 或签：任一办理人通过即推进 */
    OR,
    /** 顺序会签：按办理人顺序逐一处理，全部通过才推进 */
    SEQUENTIAL,
    /** 并行会签：所有办理人全部通过才推进 */
    PARALLEL,
    /** 票签：通过率达到 approveCount / 总数 阈值才推进 */
    VOTE,
    /** P1-5: 加权票签 — 按办理人 weight 累加，权重达到阈值才推进 */
    WEIGHTED_VOTE
}
