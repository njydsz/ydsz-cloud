package com.njydsz.pmis.user.enums;

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

    private final String code;
    private final String desc;
    private final boolean terminal;

    public static AttendanceStatus fromCode(String code) {
        if (code == null) return null;
        return Arrays.stream(values()).filter(e -> e.code.equalsIgnoreCase(code)).findFirst().orElse(null);
    }
}
