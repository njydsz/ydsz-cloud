package com.njydsz.pmis.execution.enums;

/**
 * 工时录入状态
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交</li>
 *   <li>APPROVED - 已批准</li>
 *   <li>REJECTED - 已驳回</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum TimeEntryStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String desc;

    TimeEntryStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    public boolean canTransitTo(TimeEntryStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case REJECTED -> target == DRAFT;        // 驳回后允许重新编辑
            default -> false;
        };
    }

    public static TimeEntryStatus fromCode(String code) {
        if (code == null) return null;
        for (TimeEntryStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
