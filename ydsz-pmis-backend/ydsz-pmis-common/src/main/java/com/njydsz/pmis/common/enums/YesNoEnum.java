package com.njydsz.pmis.common.enums;

import lombok.Getter;

/**
 * 通用是/否枚举（P2 架构优化：统一 Yes/No 字段编码）。
 *
 * <p>数据库存储：{@code 1} = 是（YES），{@code 0} = 否（NO）。
 * 与 MyBatis-Plus {@code Boolean} 字段约定一致，
 * 实体可直接用 {@code private YesNoEnum deleted} 而非裸 boolean。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
public enum YesNoEnum {

    /** 否 / 未启用 / 逻辑删除 */
    NO(0, "否"),

    /** 是 / 已启用 / 正常 */
    YES(1, "是");

    /** 数据库存储值 */
    private final int code;

    /** 中文描述 */
    private final String desc;

    YesNoEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 由 int code 解析枚举
     *
     * @param code 数据库值
     * @return 对应枚举；未匹配返回 NO
     */
    public static YesNoEnum of(int code) {
        for (YesNoEnum v : values()) {
            if (v.code == code) {
                return v;
            }
        }
        return NO;
    }

    /**
     * 由 boolean 转为枚举
     *
     * @param flag 布尔值
     * @return YES 当 flag=true；否则 NO
     */
    public static YesNoEnum of(boolean flag) {
        return flag ? YES : NO;
    }
}