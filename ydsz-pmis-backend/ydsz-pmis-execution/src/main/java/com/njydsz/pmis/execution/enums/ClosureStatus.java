package com.njydsz.pmis.execution.enums;

/**
 * 项目结项状态
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交</li>
 *   <li>UNDER_REVIEW - 审核中</li>
 *   <li>APPROVED - 已批准</li>
 *   <li>REJECTED - 已驳回</li>
 *   <li>ARCHIVED - 已归档</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ClosureStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    UNDER_REVIEW("UNDER_REVIEW", "审核中"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回"),
    ARCHIVED("ARCHIVED", "已归档");

    private final String code;
    private final String desc;

    ClosureStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == ARCHIVED;
    }

    public boolean canTransitTo(ClosureStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == UNDER_REVIEW || target == REJECTED;
            case UNDER_REVIEW -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == ARCHIVED;
            case REJECTED -> target == SUBMITTED || target == DRAFT;
            default -> false;
        };
    }

    public static ClosureStatus fromCode(String code) {
        if (code == null) return null;
        for (ClosureStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
