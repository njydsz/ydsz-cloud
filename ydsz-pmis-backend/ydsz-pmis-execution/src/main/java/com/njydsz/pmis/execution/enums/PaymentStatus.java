package com.njydsz.pmis.execution.enums;

/**
 * 回款状态
 *
 * <ul>
 *   <li>PENDING - 待确认</li>
 *   <li>CONFIRMED - 已确认（资金到账）</li>
 *   <li>ALLOCATED - 已核销（已分配到发票）</li>
 *   <li>CANCELLED - 已取消</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum PaymentStatus {
    PENDING("PENDING", "待确认"),
    CONFIRMED("CONFIRMED", "已确认"),
    ALLOCATED("ALLOCATED", "已核销"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    PaymentStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == ALLOCATED || this == CANCELLED;
    }

    public boolean canTransitTo(PaymentStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == ALLOCATED || target == CANCELLED;
            default -> false;
        };
    }

    public static PaymentStatus fromCode(String code) {
        if (code == null) return null;
        for (PaymentStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
