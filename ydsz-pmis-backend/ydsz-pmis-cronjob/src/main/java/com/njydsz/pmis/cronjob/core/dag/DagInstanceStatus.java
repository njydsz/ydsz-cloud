package com.njydsz.pmis.cronjob.core.dag;

/**
 * DAG 实例状态枚举（P0-1 架构优化：委托到 common.DagInstanceStatus）。
 *
 * <p>保留 cronjob 模块特有枚举以兼容现有代码，内部映射到统一枚举。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated 请直接使用 {@link com.njydsz.pmis.common.dag.DagInstanceStatus}
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public enum DagInstanceStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    PARTIAL_SUCCESS,
    PAUSED,
    CANCELED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == PARTIAL_SUCCESS || this == CANCELED;
    }

    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
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

    /**
     * 转换为统一的 {@link com.njydsz.pmis.common.dag.DagInstanceStatus}。
     */
    public com.njydsz.pmis.common.dag.DagInstanceStatus toCommon() {
        return com.njydsz.pmis.common.dag.DagInstanceStatus.valueOf(this.name());
    }
}
