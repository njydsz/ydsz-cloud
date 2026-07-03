package com.njydsz.pmis.userinfo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 出勤状态
 *
 * <p>NORMAL=正常; LATE=迟到; EARLY=早退; ABSENT=缺勤; LEAVE=请假; OVERTIME=加班。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum AttendanceStatus {

    NORMAL("NORMAL", "正常", false),
    LATE("LATE", "迟到", false),
    EARLY("EARLY", "早退", false),
    ABSENT("ABSENT", "缺勤", false),
    LEAVE("LEAVE", "请假", false),
    OVERTIME("OVERTIME", "加班", false);

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;
    /** 是否终态 */
    private final boolean terminal;

    /**
     * 根据编码解析枚举
     *
     * @param code 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；code 为 null 或无匹配时返回 null
     */
    public static AttendanceStatus fromCode(String code) {
        if (code == null) return null;
        return Arrays.stream(values()).filter(e -> e.code.equalsIgnoreCase(code)).findFirst().orElse(null);
    }
}
