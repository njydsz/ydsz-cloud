package com.njydsz.project.domain.enums;

/**
 * 立项阶段
 *
 * @author ydsz-team
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

    /**
     * 判断当前状态是否为终态（不可再迁移）。
     *
     * @return 终态（REJECTED/CLOSED）返回 true，否则返回 false
     */
    public boolean isTerminal() {
        return this == REJECTED || this == CLOSED;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态。
     *
     * <p>REJECTED 可回退到 PRE_INITIATION 重新发起；CLOSED 为终态不可迁移。
     *
     * @param target 目标状态，为 null 时返回 false
     * @return 允许迁移返回 true，否则返回 false
     */
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

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static InitiationStage fromCode(String code) {
        if (code == null) return null;
        for (InitiationStage s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
