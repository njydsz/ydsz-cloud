package com.njydsz.pmis.common.dag;

/**
 * 统一 DAG 节点失败处理策略枚举（P0-1 架构优化）。
 *
 * <p>合并 agent 的 {@code DagFailureStrategy}（CONTINUE/ABORT/RETRY）
 * 和 cronjob 的 {@code FailStrategy}（FAIL_FAST/CONTINUE_ON_FAIL/RETRY/SKIP_SUBSEQUENT）。
 *
 * <h3>策略说明</h3>
 * <ul>
 *   <li>{@link #CONTINUE}：失败节点的直接下游标记为 SKIPPED，
 *       但无依赖关系的其他分支仍继续执行
 *       （等同 agent CONTINUE / cronjob CONTINUE_ON_FAIL）</li>
 *   <li>{@link #ABORT}：立即中止整个 DAG，所有未完成节点标记为 SKIPPED
 *       （等同 agent ABORT / cronjob FAIL_FAST）</li>
 *   <li>{@link #RETRY}：重试失败节点，达到 maxRetries 后按 ABORT 处理</li>
 *   <li>{@link #SKIP_SUBSEQUENT}：仅跳过失败节点的直接后继，其他分支继续执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-1)
 */
public enum DagFailureStrategy {

    /**
     * 继续执行下游节点（容错优先）。
     * <p>对应 cronjob 的 CONTINUE_ON_FAIL / agent 的 CONTINUE。
     */
    CONTINUE,

    /**
     * 立即中止整个 DAG（强一致优先）。
     * <p>对应 cronjob 的 FAIL_FAST / agent 的 ABORT。
     */
    ABORT,

    /**
     * 重试失败节点（最多 maxRetries 次）。
     * <p>重试成功则继续；重试耗尽后按 ABORT 处理。
     */
    RETRY,

    /**
     * 跳过失败节点的所有直接后继。
     * <p>与 ABORT 的区别：ABORT 跳过 DAG 中所有未完成节点，
     * SKIP_SUBSEQUENT 只跳过失败节点的直接后继，其他分支继续执行。
     */
    SKIP_SUBSEQUENT;

    /**
     * 解析策略字符串，大小写不敏感；无效值返回 {@link #ABORT}（默认保守策略）。
     *
     * @param value 策略字符串（可为 null）
     * @return 对应枚举值
     */
    public static DagFailureStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return ABORT;
        }
        String upper = value.trim().toUpperCase();
        // 兼容 cronjob 模块旧枚举名
        return switch (upper) {
            case "FAIL_FAST" -> ABORT;
            case "CONTINUE_ON_FAIL" -> CONTINUE;
            default -> {
                try {
                    yield DagFailureStrategy.valueOf(upper);
                } catch (IllegalArgumentException e) {
                    yield ABORT;
                }
            }
        };
    }

    /**
     * 判断边级策略下是否应触发后继。
     *
     * <p>边级策略仅识别 {@link #CONTINUE}（触发）；
     * 其他策略在边级等同于不触发。
     *
     * @return true 表示前置失败时仍触发后继
     */
    public boolean shouldTriggerOnFailure() {
        return this == CONTINUE;
    }
}
