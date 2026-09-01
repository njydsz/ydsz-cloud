package com.njydsz.agent.domain.teamrun;

/**
 * Team Run 成员状态枚举。
 *
 * <p>定义单个 Agent 在 Team Run 中的执行状态。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public enum TeamRunMemberStatus {

    /** 待执行 */
    PENDING("PENDING", "待执行"),

    /** 正在执行 */
    RUNNING("RUNNING", "执行中"),

    /** 执行完成 */
    COMPLETED("COMPLETED", "已完成"),

    /** 执行失败 */
    FAILED("FAILED", "执行失败"),

    /** 已跳过（前置条件不满足等） */
    SKIPPED("SKIPPED", "已跳过"),

    /** 已取消 */
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;

    TeamRunMemberStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据状态码查找枚举。
     *
     * @param code 状态码
     * @return 对应枚举，未找到返回 null
     */
    public static TeamRunMemberStatus fromCode(String code) {
        for (TeamRunMemberStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为终态。
     *
     * @return 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED || this == CANCELLED;
    }
}
