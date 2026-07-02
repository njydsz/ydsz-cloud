package com.njydsz.pmis.execution.enums;

/**
 * 通用审批状态
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交</li>
 *   <li>APPROVED - 已批准</li>
 *   <li>REJECTED - 已驳回</li>
 *   <li>PAID - 已支付</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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

    /**
     * 判断是否为终态
     *
     * @return true 表示当前状态为终态（已批准/已驳回/已支付），不可再迁移
     */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == PAID;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
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

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static ApprovalStatus fromCode(String code) {
        if (code == null) return null;
        for (ApprovalStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
