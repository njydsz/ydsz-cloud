package com.njydsz.pmis.project.enums;

/**
 * 合同状态
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ContractStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVING("APPROVING", "审批中"),
    ACTIVE("ACTIVE", "生效中"),
    SUSPENDED("SUSPENDED", "已挂起"),
    EXPIRED("EXPIRED", "已到期"),
    TERMINATED("TERMINATED", "已终止");

    private final String code;
    private final String desc;

    ContractStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == EXPIRED || this == TERMINATED;
    }

    public boolean canTransitTo(ContractStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVING;
            case APPROVING -> target == ACTIVE || target == DRAFT;
            case ACTIVE -> target == SUSPENDED || target == EXPIRED || target == TERMINATED;
            case SUSPENDED -> target == ACTIVE || target == TERMINATED;
            default -> false;
        };
    }

    public static ContractStatus fromCode(String code) {
        if (code == null) return null;
        for (ContractStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
