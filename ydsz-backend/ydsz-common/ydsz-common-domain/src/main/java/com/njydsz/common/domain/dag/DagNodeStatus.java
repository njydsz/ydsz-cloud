package com.njydsz.common.domain.dag;

/**
 * DAG 节点状态枚举
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DagNodeStatus {

    /** 待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 等待审批 */
    WAITING_FOR_APPROVAL,
    /** 审批驳回 */
    APPROVAL_REJECTED,
    /** 重试中 */
    RETRYING,
    /** 成功 */
    SUCCESS,
    /** 失败 */
    FAILED,
    /** 跳过 */
    SKIPPED,
    /** 超时 */
    TIMEOUT;

    /**
     * 解析字符串为枚举值（大小写不敏感，容忍 null）
     *
     * @param value 状态字符串
     * @return 枚举值，null 时返回 null
     */
    public static DagNodeStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DagNodeStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 判断是否为终态（不会再发生变化的状态）
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED
                || this == TIMEOUT || this == APPROVAL_REJECTED;
    }
}
