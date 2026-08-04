package com.remisoft.cronjob.domain.dag;

import com.remisoft.common.domain.enums.BaseStatusEnum;

/**
 * DAG 实例状态枚举
 *
 * <p>实现 {@link BaseStatusEnum} 契约，提供 {@link #canTransitTo} 状态流转校验
 * 与 {@link #isTerminal()} 终态判定，供 DAG 执行引擎与业务层复用。
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum DagInstanceStatus implements BaseStatusEnum<DagInstanceStatus> {

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
    @Override
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == PARTIAL_SUCCESS
                || this == CANCELLED || this == TIMEOUT;
    }

    /**
     * 校验状态流转是否合法。
     *
     * <p>流转规则：
     * <ul>
     *   <li>PENDING → RUNNING / CANCELLED</li>
     *   <li>RUNNING → PAUSED / SUCCESS / FAILED / PARTIAL_SUCCESS / CANCELLED / TIMEOUT</li>
     *   <li>PAUSED → RUNNING / CANCELLED / TIMEOUT</li>
     *   <li>SUCCESS / FAILED / PARTIAL_SUCCESS / CANCELLED / TIMEOUT 为终态，不可再流转</li>
     * </ul>
     *
     * @param target 目标状态
     * @return true 表示允许流转
     */
    @Override
    public boolean canTransitTo(DagInstanceStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == PAUSED || target == SUCCESS || target == FAILED
                    || target == PARTIAL_SUCCESS || target == CANCELLED || target == TIMEOUT;
            case PAUSED -> target == RUNNING || target == CANCELLED || target == TIMEOUT;
            case SUCCESS, FAILED, PARTIAL_SUCCESS, CANCELLED, TIMEOUT -> false;
        };
    }
}
