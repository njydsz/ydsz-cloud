package com.njydsz.pmis.sales.enums.contract;

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

    /**
     * 判断当前状态是否为终态（不可再迁移）。
     *
     * @return 终态（EXPIRED/TERMINATED）返回 true，否则返回 false
     */
    public boolean isTerminal() {
        return this == EXPIRED || this == TERMINATED;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态。
     *
     * <p>终态不可迁移；APPROVING 可回退到 DRAFT；SUSPENDED 可恢复到 ACTIVE。
     *
     * @param target 目标状态，为 null 时返回 false
     * @return 允许迁移返回 true，否则返回 false
     */
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

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static ContractStatus fromCode(String code) {
        if (code == null) return null;
        for (ContractStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
