package com.remisoft.cronjob.domain.enums;

import com.remisoft.common.domain.enums.BaseStatusEnum;

/**
 * 子任务执行状态枚举。
 *
 * <p>对应 {@code remi_job_task.task_status} 字段（PENDING/RUNNING/SUCCESS/FAILED），
 * 实现 {@link BaseStatusEnum} 契约，提供状态流转校验。
 *
 * <p><b>状态流转：</b>
 * <pre>
 *   PENDING ──▶ RUNNING ──▶ SUCCESS
 *                   │
 *                   └────▶ FAILED
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum JobTaskStatusEnum implements BaseStatusEnum<JobTaskStatusEnum> {

    /** 待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 成功（终态） */
    SUCCESS,
    /** 失败（终态） */
    FAILED;

    /**
     * 解析字符串为枚举值（大小写不敏感，容忍 null）。
     *
     * @param value 状态字符串（如 "RUNNING"）
     * @return 枚举值，无法解析时返回 null
     */
    public static JobTaskStatusEnum parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JobTaskStatusEnum.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 是否为终态。
     *
     * @return SUCCESS / FAILED 返回 true
     */
    @Override
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    /**
     * 校验状态流转是否合法。
     *
     * <p>流转规则：PENDING → RUNNING；RUNNING → SUCCESS / FAILED；终态不可再流转。
     *
     * @param target 目标状态
     * @return true 表示允许流转
     */
    @Override
    public boolean canTransitTo(JobTaskStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == RUNNING;
            case RUNNING -> target == SUCCESS || target == FAILED;
            case SUCCESS, FAILED -> false;
        };
    }
}
