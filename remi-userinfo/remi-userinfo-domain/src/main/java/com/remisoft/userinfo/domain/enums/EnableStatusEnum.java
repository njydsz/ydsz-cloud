package com.remisoft.userinfo.domain.enums;

import com.remisoft.common.domain.enums.BaseStatusEnum;

/**
 * 启用/停用状态枚举（通用）。
 *
 * <p>对应 Role / Menu / Department / Company / Post / Language 等实体的
 * {@code status} 字段（存储值 {@code "ENABLED" / "DISABLED"}），
 * 实现 {@link BaseStatusEnum} 契约，提供统一的状态流转校验。
 *
 * <p><b>状态流转：</b>{@code ENABLED ⇄ DISABLED}（双向可切换，均非终态）。
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum EnableStatusEnum implements BaseStatusEnum<EnableStatusEnum> {

    /** 启用 */
    ENABLED,
    /** 停用 */
    DISABLED;

    /**
     * 解析字符串为枚举值（大小写不敏感，容忍 null 与空串）。
     *
     * @param value 状态字符串（如 "ENABLED"）
     * @return 枚举值，无法解析时返回 null
     */
    public static EnableStatusEnum parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EnableStatusEnum.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 是否启用。
     *
     * @return 当前为 ENABLED 返回 true
     */
    public boolean isEnabled() {
        return this == ENABLED;
    }

    /**
     * 校验状态流转是否合法。
     *
     * <p>启用与停用可互相切换。
     *
     * @param target 目标状态
     * @return true 表示允许流转
     */
    @Override
    public boolean canTransitTo(EnableStatusEnum target) {
        return this == target || this == ENABLED && target == DISABLED || this == DISABLED && target == ENABLED;
    }
}
