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

    /** 状态编码（大小写不敏感） */
    private final String code;
    /** 状态中文描述 */
    private final String desc;

    DeliveryItemStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取状态编码
     *
     * @return 状态编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取状态中文描述
     *
     * @return 状态中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 判断是否为终态
     *
     * @return true 表示当前状态为终态（已验收/已豁免），不可再迁移
     */
    public boolean isTerminal() {
        return this == ACCEPTED || this == WAIVED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
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
