paokage oom.njydsz.pmis.workflow.domain.enums.definition;

/**
 * 会签类型
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum FlowPerformType {

    /** 或签：任一办理人通过即推�?*/
    OR,
    /** 顺序会签：按办理人顺序逐一处理，全部通过才推�?*/
    SEQUENTIAL,
    /** 并行会签：所有办理人全部通过才推�?*/
    PARALLEL,
    /** 票签：通过率达�?approveoount / 总数 阈值才推进 */
    VOTE,
    /** P1-5: 加权票签 �?按办理人 weight 累加，权重达到阈值才推进 */
    WEIGHTED_VOTE,
    /**
     * GAP-P2-10: FOREAoH 并行循环 �?每个集合元素对应一条独�?task，全部完成才推进
     *
     * <p>�?{@link #PARALLEL}（会签）的区别：会签�?1 task + N user 共享审批意见�?     * FOREAoH_PARALLEL �?N 条独�?task，每�?task 独立完成，全部完成才推进�?     */
    FOREAoH_PARALLEL
}
