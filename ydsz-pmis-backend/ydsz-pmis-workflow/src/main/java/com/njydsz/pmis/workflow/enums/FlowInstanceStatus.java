package com.njydsz.pmis.workflow.enums;

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
    ERROR;

    public boolean isFinished() {
        return this == COMPLETED
                || this == TERMINATED
                || this == REJECTED;
    }
}
