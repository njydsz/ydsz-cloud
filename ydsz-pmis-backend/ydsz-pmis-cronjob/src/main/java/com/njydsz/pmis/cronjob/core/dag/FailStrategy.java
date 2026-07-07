package com.njydsz.pmis.cronjob.core.dag;

/**
 * 失败传播策略枚举（P4-3 DAG 工作流，P2-6 增强）。
 *
 * <p>定义前置任务失败后，后继任务的处理方式。可作为 DAG 级默认策略（{@code pmis_job_dag.fail_strategy}）
 * 或边级覆盖策略（{@link DagEdge#failStrategy()}）。
 *
 * <h3>策略说明</h3>
 * <ul>
 *   <li>{@link #FAIL_FAST}：前置失败则不触发后继，并标记所有未完成节点为 SKIPPED（默认，关键链路）</li>
 *   <li>{@link #CONTINUE_ON_FAIL}：前置失败仍触发后继（通知/清理类后继）</li>
 *   <li>{@link #RETRY}（P2-6）：节点失败时自动重试，达到 maxRetries 后按 FAIL_FAST 处理</li>
 *   <li>{@link #SKIP_SUBSEQUENT}（P2-6）：节点失败时跳过该节点的所有直接后继（标记 SKIPPED），
 *       但不影响间接后继（其他分支继续执行）</li>
 * </ul>
 *
 * <h3>边级 vs DAG 级</h3>
 * <ul>
 *   <li>DAG 级策略（{@code pmis_job_dag.fail_strategy}）：作用于节点失败后的整体处理</li>
 *   <li>边级策略（{@link DagEdge#failStrategy()}）：仅作用于"前置失败时这条边是否触发后继"，
 *       仅识别 {@link #FAIL_FAST}（不触发）和 {@link #CONTINUE_ON_FAIL}（触发）；
 *       {@link #RETRY} / {@link #SKIP_SUBSEQUENT} 在边级等同于 {@link #FAIL_FAST}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FailStrategy {

    /** 前置失败则不触发后继，并标记所有未完成节点为 SKIPPED（默认，关键链路） */
    FAIL_FAST,

    /** 前置失败仍触发后继（适用于通知/清理类后继） */
    CONTINUE_ON_FAIL,

    /**
     * P2-6: 节点失败时自动重试。
     *
     * <p>重试次数由节点实例的 {@code maxRetries} 控制（来源于 {@code JobDO.maxRetries}）。
     * 达到 maxRetries 后按 {@link #FAIL_FAST} 处理。
     */
    RETRY,

    /**
     * P2-6: 节点失败时跳过该节点的所有直接后继。
     *
     * <p>与 {@link #FAIL_FAST} 的区别：FAIL_FAST 跳过 DAG 中所有未完成节点，
     * SKIP_SUBSEQUENT 只跳过失败节点的直接后继，其他分支继续执行。
     * 适用于多分支 DAG 中某分支失败不影响其他分支的场景。
     */
    SKIP_SUBSEQUENT;

    /**
     * 解析策略字符串，大小写不敏感；无效值返回 {@link #FAIL_FAST}。
     *
     * @param value 策略字符串（可为 null）
     * @return 对应枚举值
     */
    public static FailStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return FAIL_FAST;
        }
        try {
            return FailStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FAIL_FAST;
        }
    }

    /**
     * P2-6: 判断边级策略下是否应触发后继。
     *
     * <p>边级策略仅识别 {@link #FAIL_FAST}（不触发）和 {@link #CONTINUE_ON_FAIL}（触发）；
     * {@link #RETRY} / {@link #SKIP_SUBSEQUENT} 在边级等同于 {@link #FAIL_FAST}。
     *
     * @return true 表示前置失败时仍触发后继
     */
    public boolean shouldTriggerOnFailure() {
        return this == CONTINUE_ON_FAIL;
    }
}
