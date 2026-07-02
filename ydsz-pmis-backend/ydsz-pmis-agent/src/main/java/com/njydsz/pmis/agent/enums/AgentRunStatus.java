package com.njydsz.pmis.agent.enums;

/**
 * AI 预测/推荐执行状态
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AgentRunStatus {
    /** 等待执行 */
    PENDING("PENDING", "等待执行"),
    /** 执行中 */
    RUNNING("RUNNING", "执行中"),
    /** 成功（终态） */
    SUCCESS("SUCCESS", "成功"),
    /** 失败（终态） */
    FAILED("FAILED", "失败");

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;

    AgentRunStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取枚举编码。
     *
     * @return 枚举编码
     */
    public String getCode() { return code; }
    /**
     * 获取枚举描述。
     *
     * @return 枚举描述
     */
    public String getDesc() { return desc; }

    /**
     * 判断当前状态是否为终态（不可再迁移）。
     *
     * @return 终态（SUCCESS/FAILED）返回 true，否则返回 false
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态。
     *
     * <p>终态不可迁移；PENDING→RUNNING；RUNNING→SUCCESS/FAILED。
     *
     * @param target 目标状态，为 null 时返回 false
     * @return 允许迁移返回 true，否则返回 false
     */
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

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static AgentRunStatus fromCode(String code) {
        if (code == null) return null;
        for (AgentRunStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
