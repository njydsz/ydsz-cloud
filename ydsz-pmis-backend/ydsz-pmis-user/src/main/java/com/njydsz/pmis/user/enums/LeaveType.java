package com.njydsz.pmis.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 请假类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum LeaveType {

    ANNUAL("ANNUAL", "年假"),
    SICK("SICK", "病假"),
    PERSONAL("PERSONAL", "事假"),
    MARRIAGE("MARRIAGE", "婚假"),
    MATERNITY("MATERNITY", "产假/陪产假"),
    BEREAVEMENT("BEREAVEMENT", "丧假"),
    OTHER("OTHER", "其他");

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;

    /**
     * 根据编码解析枚举
     *
     * @param code 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；code 为 null 或无匹配时返回 null
     */
    public static LeaveType fromCode(String code) {
        if (code == null) return null;
        return Arrays.stream(values()).filter(e -> e.code.equalsIgnoreCase(code)).findFirst().orElse(null);
    }
}
