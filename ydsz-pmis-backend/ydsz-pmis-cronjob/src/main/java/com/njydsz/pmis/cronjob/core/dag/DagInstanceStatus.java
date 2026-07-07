package com.njydsz.pmis.cronjob.core.dag;

/**
 * DAG 实例状态枚举（P2 DAG 增强）。
 *
 * <p>状态流转：
 * <pre>
 * PENDING → RUNNING → SUCCESS（全部节点成功）
 *                  → FAILED（FAIL_FAST 中止）
 *                  → PARTIAL_SUCCESS（部分节点失败但未中止）
 *                  → PAUSED（手动暂停）→ RUNNING（恢复）
 *                  → CANCELED（手动取消）
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DagInstanceStatus {

    /** 待执行（已创建实例但尚未开始） */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 成功（所有节点 SUCCESS） */
    SUCCESS,

    /** 失败（FAIL_FAST 中止或关键节点失败） */
    FAILED,

    /** 部分成功（部分节点失败但 DAG 级策略为 CONTINUE_ON_FAIL） */
    PARTIAL_SUCCESS,

    /** 暂停（手动暂停，可恢复） */
    PAUSED,

    /** 取消（手动取消，不可恢复） */
    CANCELED;

    /**
     * 判断是否为终态（不可再变更）。
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == PARTIAL_SUCCESS || this == CANCELED;
    }

    /**
     * 判断是否为活跃态（可继续推进）。
     */
    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }

    /**
     * 安全解析状态字符串，无效值返回 null。
     */
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
