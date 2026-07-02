package com.njydsz.pmis.execution.enums;

/**
 * 交付物状态
 *
 * <ul>
 *   <li>PENDING - 待提交</li>
 *   <li>SUBMITTED - 已提交</li>
 *   <li>UNDER_REVIEW - 评审中</li>
 *   <li>ACCEPTED - 已验收</li>
 *   <li>REJECTED - 已驳回</li>
 *   <li>WAIVED - 已豁免</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DeliveryItemStatus {
    PENDING("PENDING", "待提交"),
    SUBMITTED("SUBMITTED", "已提交"),
    UNDER_REVIEW("UNDER_REVIEW", "评审中"),
    ACCEPTED("ACCEPTED", "已验收"),
    REJECTED("REJECTED", "已驳回"),
    WAIVED("WAIVED", "已豁免");

    private final String code;
    private final String desc;

    DeliveryItemStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == ACCEPTED || this == WAIVED;
    }

    public boolean canTransitTo(DeliveryItemStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case PENDING -> target == SUBMITTED || target == WAIVED;
            case SUBMITTED -> target == UNDER_REVIEW || target == ACCEPTED
                    || target == REJECTED || target == WAIVED;
            case UNDER_REVIEW -> target == ACCEPTED || target == REJECTED;
            case REJECTED -> target == SUBMITTED || target == WAIVED;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static DeliveryItemStatus fromCode(String code) {
        if (code == null) return null;
        for (DeliveryItemStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
