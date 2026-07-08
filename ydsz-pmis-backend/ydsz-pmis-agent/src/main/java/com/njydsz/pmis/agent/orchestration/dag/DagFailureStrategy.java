package com.njydsz.pmis.agent.orchestration.dag;

/**
 * DAG 节点失败处理策略（P3-2 落地）。
 *
 * <p>当某节点执行失败时，决定后续行为。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
public enum DagFailureStrategy {

    /**
     * 继续执行下游节点（容错优先）。
     *
     * <p>失败节点的直接下游会被标记为 SKIPPED，
     * 但无依赖关系的其他分支仍继续执行。
     */
    CONTINUE,

    /**
     * 立即中止整个 DAG（强一致优先）。
     *
     * <p>一旦某节点失败，所有未完成的节点标记为 SKIPPED，
     * DAG 实例状态置为 FAILED。
     */
    ABORT,

    /**
     * 重试失败节点（最多 maxRetries 次）。
     *
     * <p>重试成功则继续；重试耗尽后按 {@link #CONTINUE} 或 {@link #ABORT} 处理
     * （由 maxRetries 耗尽后的兜底策略决定，默认 ABORT）。
     */
    RETRY;
}
