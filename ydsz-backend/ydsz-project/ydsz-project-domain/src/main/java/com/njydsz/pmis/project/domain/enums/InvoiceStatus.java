package com.njydsz.project.domain.enums;

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
 * @author ydsz-team
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

    /** 状态编码（大小写不敏感） */
    private final String code;
    /** 状态中文描述 */
    private final String desc;

    InvoiceStatus(String code, String desc) {
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
     * <p>ISSUED 虽为终态但允许红冲，因此不视为纯终态；RED_REVERSED/CANCELLED 不可再迁移
     *
     * @return true 表示当前状态为终态，不可再迁移
     */
    public boolean isTerminal() {
        // ISSUED 虽为终态但允许红冲，因此不视为纯终态；RED_REVERSED/CANCELLED 不可再迁移
        return this == RED_REVERSED || this == CANCELLED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
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

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static InvoiceStatus fromCode(String code) {
        if (code == null) return null;
        for (InvoiceStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
