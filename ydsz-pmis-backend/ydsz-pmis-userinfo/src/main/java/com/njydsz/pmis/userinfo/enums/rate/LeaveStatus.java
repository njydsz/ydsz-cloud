package com.njydsz.pmis.userinfo.enums.rate;

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

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;
    /** 是否终态 */
    private final boolean terminal;

    /**
     * 判断当前状态是否可流转到目标状态
     *
     * @param target 目标状态
     * @return 允许流转返回 true，否则返回 false
     */
    public boolean canTransitTo(LeaveStatus target) {
        if (this == target) return false;
        return switch (this) {
            case DRAFT -> target == SUBMITTED || target == CANCELLED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case REJECTED -> target == DRAFT || target == SUBMITTED;
            default -> false;
        };
    }

    /**
     * 根据编码解析枚举
     *
     * @param code 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；code 为 null 或无匹配时返回 null
     */
    public static LeaveStatus fromCode(String code) {
        if (code == null) return null;
        return Arrays.stream(values()).filter(e -> e.code.equalsIgnoreCase(code)).findFirst().orElse(null);
    }
}
