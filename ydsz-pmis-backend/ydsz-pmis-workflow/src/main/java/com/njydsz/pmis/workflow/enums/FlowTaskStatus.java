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
    FROZEN;

    public boolean isFinished() {
        return this == COMPLETED
                || this == REJECTED
                || this == SKIPPED
                || this == CANCELLED
                || this == TIMEOUT;
    }
}
