package com.njydsz.pmis.common.dag;

/**
 * 统一 DAG 实例整体执行状态枚举（P0-1 架构优化）。
 *
 * <p>合并 cronjob 和 agent 两个模块的实例状态枚举，消除重复定义。
 *
 * <p>状态流转：
 * <pre>
 * CREATED/PENDING → RUNNING → SUCCESS（全部节点成功）
 *                        → FAILED（中止或关键节点失败）
 *                        → PARTIAL_SUCCESS（部分节点失败但未中止）
 *                        → PAUSED（手动暂停）→ RUNNING（恢复）
 *                        → CANCELLED/CANCELED（手动取消）
 *                        → TIMEOUT（超时）
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-1)
 */
public enum DagInstanceStatus {

    /** 已创建未启动 */
    CREATED,

    /** 待执行（已创建实例但尚未开始，等同 CREATED） */
    PENDING,

    /** 运行中 */
    RUNNING,

    /** 成功（所有节点 SUCCESS） */
    SUCCESS,

    /** 失败（中止或关键节点失败） */
    FAILED,

    /** 部分成功（部分节点失败但 DAG 级策略为 CONTINUE_ON_FAIL） */
    PARTIAL_SUCCESS,

    /** 暂停（手动暂停，可恢复） */
    PAUSED,

    /** 取消（手动取消，不可恢复） */
    CANCELLED,

    /** 取消（CANCELED 别名，兼容 cronjob 模块） */
    CANCELED,

    /** 超时 */
    TIMEOUT;

    /**
     * 判断是否为终态（不可再变更）。
     *
     * @return true 表示 DAG 实例不再变化
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == PARTIAL_SUCCESS
                || this == CANCELLED || this == CANCELED || this == TIMEOUT;
    }

    /**
     * 判断是否为活跃态（可继续推进）。
     *
     * @return true 表示实例处于可推进状态
     */
    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }

    /**
     * 安全解析状态字符串，无效值返回 null。
     *
     * @param value 状态字符串
     * @return 对应枚举值；无效返回 null
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
