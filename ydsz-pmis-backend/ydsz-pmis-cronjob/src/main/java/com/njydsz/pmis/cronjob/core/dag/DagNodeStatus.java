package com.njydsz.pmis.cronjob.core.dag;

/**
 * DAG 节点实例状态枚举（P0-1 架构优化：委托到 common.DagNodeStatus）。
 *
 * <p>保留 cronjob 模块特有枚举以兼容现有代码，内部映射到统一枚举。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated 请直接使用 {@link com.njydsz.pmis.common.dag.DagNodeStatus}
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public enum DagNodeStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
    RETRYING,
    WAITING_FOR_APPROVAL,
    APPROVAL_REJECTED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == APPROVAL_REJECTED;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isFailed() {
        return this == FAILED || this == SKIPPED || this == APPROVAL_REJECTED;
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

    /**
     * 转换为统一的 {@link com.njydsz.pmis.common.dag.DagNodeStatus}。
     */
    public com.njydsz.pmis.common.dag.DagNodeStatus toCommon() {
        return com.njydsz.pmis.common.dag.DagNodeStatus.valueOf(this.name());
    }
}
