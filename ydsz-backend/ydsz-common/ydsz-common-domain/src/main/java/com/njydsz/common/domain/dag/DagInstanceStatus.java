package com.njydsz.common.domain.dag;

/**
 * DAG 实例状态枚举
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DagInstanceStatus {

    /** 待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 已暂停 */
    PAUSED,
    /** 成功 */
    SUCCESS,
    /** 失败 */
    FAILED,
    /** 部分成功 */
    PARTIAL_SUCCESS,
    /** 已取消 */
    CANCELLED,
    /** 超时 */
    TIMEOUT;

    /**
     * 解析字符串为枚举值（大小写不敏感，容忍 null）
     *
     * @param value 状态字符串
     * @return 枚举值，null 时返回 null
     */
    public static DagInstanceStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DagInstanceStatus.valueOf(value.toUpperCase());
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
        return this == SUCCESS || this == FAILED || this == PARTIAL_SUCCESS
                || this == CANCELLED || this == TIMEOUT;
    }
}
