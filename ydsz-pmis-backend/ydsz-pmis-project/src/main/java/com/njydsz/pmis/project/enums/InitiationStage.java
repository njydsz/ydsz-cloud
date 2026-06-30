package com.njydsz.pmis.project.enums;

/**
 * 立项阶段
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum InitiationStage {
    PRE_INITIATION("PRE_INITIATION", "预立项"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVING("APPROVING", "审批中"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回"),
    EXECUTING("EXECUTING", "执行中"),
    CLOSED("CLOSED", "已结项");

    private final String code;
    private final String desc;

    InitiationStage(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == REJECTED || this == CLOSED;
    }

    public boolean canTransitTo(InitiationStage target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this == REJECTED) {
            return target == PRE_INITIATION;  // 驳回后可以重新发起
        }
        if (this == CLOSED) return false;
        return switch (this) {
            case PRE_INITIATION -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVING || target == REJECTED;
            case APPROVING -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == EXECUTING || target == CLOSED;
            case EXECUTING -> target == CLOSED;
            default -> false;
        };
    }

    public static InitiationStage fromCode(String code) {
        if (code == null) return null;
        for (InitiationStage s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
