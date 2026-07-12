package com.njydsz.pmis.workflow.domain.enums.instance;

/**
 * 流程实例状态
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FlowInstanceStatus {

    /** 运行中 */
    RUNNING,
    /** 挂起 */
    SUSPENDED,
    /** 已完成 */
    COMPLETED,
    /** 已终止（管理员强制） */
    TERMINATED,
    /** 已驳回（被退回并最终结束） */
    REJECTED,
    /** P1-4: 异常（服务节点执行失败等异常状态，需人工介入处理） */
    ERROR,
    /**
     * P2-3: 已回滚（已完成实例由发起人/管理员撤销，业务侧可据此做补偿）
     *
     * <p>语义：原本已 COMPLETED 的实例被撤销，最终态；流程不再运行，但保留全部历史轨迹，
     * 供业务侧（如 ProjectInitiationFlowListener）感知并执行回滚补偿逻辑。
     */
    ROLLED_BACK;

    public boolean isFinished() {
        return this == COMPLETED
                || this == TERMINATED
                || this == REJECTED
                || this == ROLLED_BACK;
    }
}
