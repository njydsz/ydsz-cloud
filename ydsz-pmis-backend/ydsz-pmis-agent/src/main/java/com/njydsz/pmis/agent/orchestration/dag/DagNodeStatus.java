package com.njydsz.pmis.agent.orchestration.dag;

/**
 * DAG 节点执行状态机（P3-2 落地）。
 *
 * <p>合法流转：
 * <ul>
 *   <li>{@link #PENDING} → {@link #RUNNING}（被调度器选中）</li>
 *   <li>{@link #RUNNING} → {@link #SUCCESS} / {@link #FAILED} / {@link #SKIPPED}</li>
 *   <li>{@link #FAILED}  → {@link #RUNNING}（重试，仅 RETRY 策略）</li>
 *   <li>{@link #PENDING} → {@link #SKIPPED}（条件不满足或上游失败导致跳过）</li>
 * </ul>
 *
 * <p>终态：{@link #SUCCESS} / {@link #FAILED} / {@link #SKIPPED}（非重试场景下）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
public enum DagNodeStatus {

    /** 待执行（初始态） */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 执行成功（终态） */
    SUCCESS,

    /** 执行失败（终态或待重试） */
    FAILED,

    /** 跳过（条件不满足或上游失败，终态） */
    SKIPPED;

    /**
     * 判断是否为终态（不可再流转）。
     *
     * <p>注意：{@link #FAILED} 在 RETRY 策略下可流转回 {@link #RUNNING}，
     * 因此不算严格终态；本方法仅在非重试场景下用于判断。
     *
     * @return true 表示该状态不再变化（SUCCESS / SKIPPED）
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == SKIPPED;
    }

    /**
     * 校验状态流转是否合法。
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态流转到 target
     */
    public boolean canTransitTo(DagNodeStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == RUNNING || target == SKIPPED;
            case RUNNING -> target == SUCCESS || target == FAILED || target == SKIPPED;
            case FAILED -> target == RUNNING; // 仅 RETRY 策略
            case SUCCESS, SKIPPED -> false;   // 严格终态
        };
    }
}
