package com.njydsz.pmis.userinfo.enums.user;

import lombok.Getter;

/**
 * 雇佣类型枚举
 *
 * <p>全职 FULL_TIME：L1-L18 职级体系，成本 = 月薪 + 社保公积金 + 差旅报销 + 差旅补贴（公司承担）
 * <p>兼职 PART_TIME：P1-P18 职级体系，成本 = 月薪 + 商业保险 + 差旅报销 + 差旅补贴（公司承担）
 * <p>外包 OUTSOURCE：V1-V18 职级体系，成本 = 月薪 + 差旅报销 + 差旅补贴（公司承担）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
public enum EmployeeType {

    /** 全职 */
    FULL_TIME("FULL_TIME", "全职"),
    /** 兼职 */
    PART_TIME("PART_TIME", "兼职"),
    /** 外包 */
    OUTSOURCE("OUTSOURCE", "外包");

    private final String code;
    private final String label;

    EmployeeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据编码获取枚举值
     *
     * @param code 编码
     * @return 枚举值，不存在返回 null
     */
    public static EmployeeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (EmployeeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
