package com.njydsz.pmis.execution.enums;

/**
 * 利润测算版本状态
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交评审</li>
 *   <li>APPROVED - 已审批</li>
 *   <li>ARCHIVED - 已归档</li>
 *   <li>REJECTED - 已驳回</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum SimulationStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVED("APPROVED", "已审批"),
    ARCHIVED("ARCHIVED", "已归档"),
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String desc;

    SimulationStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == APPROVED || this == ARCHIVED || this == REJECTED;
    }

    public boolean canTransitTo(SimulationStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> target == SUBMITTED || target == REJECTED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case REJECTED -> target == DRAFT || target == SUBMITTED;
            case APPROVED -> target == ARCHIVED;
            default -> false;
        };
    }

    public static SimulationStatus fromCode(String code) {
        if (code == null) return null;
        for (SimulationStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
