package com.njydsz.project.domain.enums;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 项目变更审批状态枚举。
 *
 * <p>对应 {@code ydsz_project_change.changeStatus} 字段（DRAFT / PENDING / APPROVED / REJECTED），
 * 实现 {@link BaseStatusEnum} 契约，提供审批流状态流转校验。
 *
 * <p><b>状态流转：</b>
 * <pre>
 *   DRAFT ──▶ PENDING ──▶ APPROVED
 *                  │
 *                  └────▶ REJECTED
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ProjectChangeStatusEnum implements BaseStatusEnum<ProjectChangeStatusEnum> {

    /** 草稿 */
    DRAFT,
    /** 审批中 */
    PENDING,
    /** 已通过（终态） */
    APPROVED,
    /** 已驳回（终态） */
    REJECTED;

    /**
     * 解析字符串为枚举值（大小写不敏感，容忍 null）。
     *
     * @param value 状态字符串（如 "PENDING"）
     * @return 枚举值，无法解析时返回 null
     */
    public static ProjectChangeStatusEnum parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProjectChangeStatusEnum.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 是否为终态。
     *
     * @return APPROVED / REJECTED 返回 true
     */
    @Override
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    /**
     * 校验状态流转是否合法。
     *
     * <p>流转规则：DRAFT → PENDING；PENDING → APPROVED / REJECTED；终态不可再流转。
     *
     * @param target 目标状态
     * @return true 表示允许流转
     */
    @Override
    public boolean canTransitTo(ProjectChangeStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case DRAFT -> target == PENDING;
            case PENDING -> target == APPROVED || target == REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }
}
