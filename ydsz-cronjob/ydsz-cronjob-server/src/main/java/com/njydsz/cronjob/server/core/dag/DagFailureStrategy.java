package com.njydsz.cronjob.server.core.dag;

/**
 * DAG 失败传播策略枚举（P0-1 架构优化：统一枚举）。
 *
 * <h3>策略说明</h3>
 * <ul>
 *   <li>{@link #ABORT}：前置失败则不触发后继，并标记所有未完成节点为 SKIPPED（默认，关键链路）</li>
 *   <li>{@link #CONTINUE}：前置失败仍触发后继（适用于通知/清理类后继）</li>
 *   <li>{@link #RETRY}：节点失败时自动重试</li>
 *   <li>{@link #SKIP_SUBSEQUENT}：节点失败时跳过该节点的所有直接后继</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DagFailureStrategy {

    /** 前置失败则不触发后继，并标记所有未完成节点为 SKIPPED（默认，关键链路） */
    ABORT,

    /** 前置失败仍触发后继（适用于通知/清理类后继） */
    CONTINUE,

    /** 节点失败时自动重试 */
    RETRY,

    /** 节点失败时跳过该节点的所有直接后继 */
    SKIP_SUBSEQUENT;

    /**
     * 解析策略字符串，大小写不敏感；无效值返回 {@link #ABORT}。
     *
     * <p>兼容旧枚举名：FAIL_FAST → ABORT，CONTINUE_ON_FAIL → CONTINUE。
     *
     * @param value 策略字符串（可为 null/空白）
     * @return 对应的失败策略枚举
     */
    public static DagFailureStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return ABORT;
        }
        String normalized = value.trim().toUpperCase();
        // 兼容旧枚举名
        if ("FAIL_FAST".equals(normalized)) {
            return ABORT;
        }
        if ("CONTINUE_ON_FAIL".equals(normalized)) {
            return CONTINUE;
        }
        try {
            return DagFailureStrategy.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return ABORT;
        }
    }

    /**
     * 判断边级策略下是否应触发后继。
     *
     * @return 当策略为 {@link #CONTINUE} 时返回 true（前置失败仍触发后继）
     */
    public boolean shouldTriggerOnFailure() {
        return this == CONTINUE;
    }
}
