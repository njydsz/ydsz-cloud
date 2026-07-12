package com.njydsz.pmis.common.core.dag;

/**
 * DAG 实例状态枚举。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DagInstanceStatus {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESS,
    FAILED,
    PARTIAL_SUCCESS,
    CANCELED,
    TIMEOUT;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELED || this == TIMEOUT;
    }

    public static DagInstanceStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DagInstanceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
