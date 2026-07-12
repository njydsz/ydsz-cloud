package com.njydsz.pmis.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 数据权限范围类型枚举
 *
 * <p>定义系统中数据权限的维度类型，用于行级数据权限控制。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum DataScopeType {

    ALL(0, "全部数据"),
    TENANT(10, "租户级别"),
    USER(5, "用户级别"),
    DEPT(20, "部门级别"),
    REGION(15, "区域级别"),
    PROJECT(25, "项目级别"),
    COMPANY(30, "公司级别"),
    GROUP(40, "集团级别");

    private final int code;
    private final String description;

    /**
     * 根据 code 获取枚举值
     */
    public static DataScopeType fromCode(int code) {
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElse(null);
    }
}
