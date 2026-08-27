package com.njydsz.agent.domain.teamrun;

/**
 * Team Run 状态枚举。
 *
 * <p>定义多 Agent 协作执行的生命周期状态。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public enum TeamRunStatus {

    /** 已创建，等待启动 */
    CREATED("CREATED", "已创建"),

    /** 正在执行中 */
    RUNNING("RUNNING", "执行中"),

    /** 等待人工审批 */
    WAITING_APPROVAL("WAITING_APPROVAL", "等待审批"),

    /** 所有 Agent 执行完成 */
    COMPLETED("COMPLETED", "已完成"),

    /** 部分或全部 Agent 执行失败 */
    FAILED("FAILED", "执行失败"),

    /** 已被用户取消 */
    CANCELLED("CANCELLED", "已取消"),

    /** 执行超时被终止 */
    TIMEOUT("TIMEOUT", "执行超时");

    private final String code;
    private final String description;

    TeamRunStatus(String code, String description) {
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
    public static TeamRunStatus fromCode(String code) {
        for (TeamRunStatus status : values()) {
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
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    /**
     * 判断是否正在运行。
     *
     * @return 是否正在运行
     */
    public boolean isActive() {
        return this == RUNNING || this == WAITING_APPROVAL;
    }
}
