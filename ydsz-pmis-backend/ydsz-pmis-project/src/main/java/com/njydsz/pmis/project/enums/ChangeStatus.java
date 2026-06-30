package com.njydsz.pmis.project.enums;

/**
 * 项目变更状态
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交</li>
 *   <li>UNDER_REVIEW - 评审中</li>
 *   <li>APPROVED - 已批准</li>
 *   <li>REJECTED - 已驳回</li>
 *   <li>EXECUTING - 执行中</li>
 *   <li>EXECUTED - 已执行</li>
 *   <li>CANCELLED - 已取消</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ChangeStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    UNDER_REVIEW("UNDER_REVIEW", "评审中"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回"),
    EXECUTING("EXECUTING", "执行中"),
    EXECUTED("EXECUTED", "已执行"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    ChangeStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == EXECUTED || this == REJECTED || this == CANCELLED;
    }

    public boolean canTransitTo(ChangeStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case DRAFT -> target == SUBMITTED || target == CANCELLED;
            case SUBMITTED -> target == UNDER_REVIEW || target == CANCELLED;
            case UNDER_REVIEW -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == EXECUTING || target == CANCELLED;
            case EXECUTING -> target == EXECUTED || target == CANCELLED;
            default -> false;
        };
    }

    public static ChangeStatus fromCode(String code) {
        if (code == null) return null;
        for (ChangeStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
