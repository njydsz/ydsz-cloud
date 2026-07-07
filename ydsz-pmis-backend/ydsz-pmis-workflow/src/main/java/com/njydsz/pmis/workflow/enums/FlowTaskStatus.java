package com.njydsz.pmis.workflow.enums;

/**
 * 任务状态
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FlowTaskStatus {

    /** 待办 */
    PENDING,
    /** 已签收 */
    CLAIMED,
    /** 已通过 */
    COMPLETED,
    /** 已驳回 */
    REJECTED,
    /** 已跳过（会签场景下未轮到该用户） */
    SKIPPED,
    /** 已取消（流程终止/撤回时连带取消） */
    CANCELLED,
    /** 超时（自动处理） */
    TIMEOUT,
    /** 已委派（被委派人处理完后回到原办理人） */
    DELEGATED,
    /** 已冻结（流程挂起时连带冻结 PENDING/CLAIMED，激活后回到 PENDING） */
    FROZEN,
    /** P2-1: 已挂起（任务级挂起，激活后回到 PENDING；与 FROZEN 区别：FROZEN 由实例级挂起连带触发，SUSPENDED 由任务级独立挂起） */
    SUSPENDED,
    /** 暂存（审批人暂存审批意见草稿，不改变任务主状态，可随时提交） */
    DRAFT;

    public boolean isFinished() {
        return this == COMPLETED
                || this == REJECTED
                || this == SKIPPED
                || this == CANCELLED
                || this == TIMEOUT;
    }
}
