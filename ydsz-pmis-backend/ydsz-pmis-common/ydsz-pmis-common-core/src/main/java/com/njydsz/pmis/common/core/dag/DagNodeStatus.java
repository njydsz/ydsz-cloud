package com.njydsz.pmis.common.core.dag;

/**
 * DAG 节点状态枚举。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DagNodeStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
    WAITING_FOR_APPROVAL,
    APPROVAL_REJECTED,
    TIMEOUT,
    RETRYING;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED
                || this == APPROVAL_REJECTED || this == TIMEOUT;
    }

    public static DagNodeStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DagNodeStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
