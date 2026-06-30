package com.njydsz.pmis.execution.enums;

/**
 * WBS 任务状态
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum WbsTaskStatus {
    PLANNED("PLANNED", "已规划"),
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    BLOCKED("BLOCKED", "阻塞"),
    IN_REVIEW("IN_REVIEW", "验收中"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    WbsTaskStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean canTransitTo(WbsTaskStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case PLANNED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == BLOCKED || target == IN_REVIEW
                    || target == COMPLETED || target == CANCELLED;
            case BLOCKED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_REVIEW -> target == COMPLETED || target == IN_PROGRESS
                    || target == CANCELLED;
            default -> false;
        };
    }

    public static WbsTaskStatus fromCode(String code) {
        if (code == null) return null;
        for (WbsTaskStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
