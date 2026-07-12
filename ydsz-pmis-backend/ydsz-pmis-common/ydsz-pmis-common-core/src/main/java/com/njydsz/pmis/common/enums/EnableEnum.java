package com.njydsz.pmis.common.enums;

import lombok.Getter;

/**
 * 通用启用/停用枚举（P2 架构优化：统一 Enable/Disable 字段编码）。
 *
 * <p>用于 {@code enabled}、{@code status} 等业务状态字段的标准化。
 * 业务模块特殊状态（如 {@code EmployeeStatus}、{@code ProjectStatus}）保持独立，
 * 但通用开关（启用/停用、显示/隐藏）必须使用本枚举。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
public enum EnableEnum {

    /** 停用 / 禁用 / 隐藏 */
    DISABLED(0, "停用"),

    /** 启用 / 正常 / 显示 */
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    EnableEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 由 int code 解析枚举
     *
     * @param code 数据库值
     * @return 对应枚举；未匹配返回 DISABLED
     */
    public static EnableEnum of(int code) {
        for (EnableEnum v : values()) {
            if (v.code == code) {
                return v;
            }
        }
        return DISABLED;
    }

    /**
     * 由 boolean 转为枚举
     *
     * @param flag 布尔值
     * @return ENABLED 当 flag=true；否则 DISABLED
     */
    public static EnableEnum of(boolean flag) {
        return flag ? ENABLED : DISABLED;
    }
}