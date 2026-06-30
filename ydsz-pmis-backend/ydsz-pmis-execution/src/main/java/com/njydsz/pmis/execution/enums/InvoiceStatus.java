package com.njydsz.pmis.execution.enums;

/**
 * 发票状态
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交</li>
 *   <li>APPROVED - 已审批</li>
 *   <li>ISSUED - 已开具（财务已开）</li>
 *   <li>RED_REVERSED - 已红冲</li>
 *   <li>REJECTED - 已驳回</li>
 *   <li>CANCELLED - 已取消</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum InvoiceStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVED("APPROVED", "已审批"),
    ISSUED("ISSUED", "已开具"),
    RED_REVERSED("RED_REVERSED", "已红冲"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    InvoiceStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        // ISSUED 虽为终态但允许红冲，因此不视为纯终态；RED_REVERSED/CANCELLED 不可再迁移
        return this == RED_REVERSED || this == CANCELLED;
    }

    public boolean canTransitTo(InvoiceStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> target == SUBMITTED || target == CANCELLED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == ISSUED || target == CANCELLED;
            case ISSUED -> target == RED_REVERSED;
            case REJECTED -> target == DRAFT || target == SUBMITTED;
            default -> false;
        };
    }

    public static InvoiceStatus fromCode(String code) {
        if (code == null) return null;
        for (InvoiceStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
