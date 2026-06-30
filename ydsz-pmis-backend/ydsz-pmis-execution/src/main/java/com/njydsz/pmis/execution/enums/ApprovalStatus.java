package com.njydsz.pmis.execution.enums;

/**
 * 通用审批状态
 */
public enum ApprovalStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回"),
    PAID("PAID", "已支付");

    private final String code;
    private final String desc;

    ApprovalStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == PAID;
    }

    public boolean canTransitTo(ApprovalStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case REJECTED -> target == DRAFT;        // 驳回后允许重新编辑
            case APPROVED -> target == PAID;
            default -> false;
        };
    }

    public static ApprovalStatus fromCode(String code) {
        if (code == null) return null;
        for (ApprovalStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
