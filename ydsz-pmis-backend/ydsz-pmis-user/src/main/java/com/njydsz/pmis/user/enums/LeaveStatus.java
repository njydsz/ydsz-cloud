package com.njydsz.pmis.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 请假状态机
 *
 * <p>DRAFT → SUBMITTED → APPROVED/REJECTED → (CANCELLED)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum LeaveStatus {

    DRAFT("DRAFT", "草稿", false),
    SUBMITTED("SUBMITTED", "已提交", false),
    APPROVED("APPROVED", "已通过", true),
    REJECTED("REJECTED", "已驳回", false),
    CANCELLED("CANCELLED", "已取消", true);

    private final String code;
    private final String desc;
    private final boolean terminal;

    public boolean canTransitTo(LeaveStatus target) {
        if (this == target) return false;
        return switch (this) {
            case DRAFT -> target == SUBMITTED || target == CANCELLED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case REJECTED -> target == DRAFT || target == SUBMITTED;
            default -> false;
        };
    }

    public static LeaveStatus fromCode(String code) {
        if (code == null) return null;
        return Arrays.stream(values()).filter(e -> e.code.equalsIgnoreCase(code)).findFirst().orElse(null);
    }
}
