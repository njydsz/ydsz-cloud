package com.njydsz.pmis.agent.enums;

/**
 * AI 预测/推荐执行状态
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AgentRunStatus {
    PENDING("PENDING", "等待执行"),
    RUNNING("RUNNING", "执行中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;

    AgentRunStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public boolean canTransitTo(AgentRunStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case PENDING -> target == RUNNING;
            case RUNNING -> target == SUCCESS || target == FAILED;
            default -> false;
        };
    }

    public static AgentRunStatus fromCode(String code) {
        if (code == null) return null;
        for (AgentRunStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
