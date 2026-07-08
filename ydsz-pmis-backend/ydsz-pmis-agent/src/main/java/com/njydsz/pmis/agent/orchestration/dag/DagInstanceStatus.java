package com.njydsz.pmis.agent.orchestration.dag;

/**
 * DAG 实例整体执行状态（P3-2 落地）。
 *
 * <p>描述一次 DAG 执行的整体生命周期。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
public enum DagInstanceStatus {

    /** 已创建未启动 */
    CREATED,

    /** 运行中 */
    RUNNING,

    /** 全部节点成功 */
    SUCCESS,

    /** 部分节点失败且策略为 ABORT 或无更多可执行节点 */
    FAILED,

    /** 被外部取消 */
    CANCELLED,

    /** 超时 */
    TIMEOUT;

    /**
     * 判断是否为终态。
     *
     * @return true 表示 DAG 实例不再变化
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }
}
