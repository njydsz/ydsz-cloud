package com.njydsz.agent.domain.runtime;

/**
 * Agent 运行时会话状态枚举。
 *
 * <p>定义 Agent 执行会话在其生命周期中可能处于的所有状态。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public enum RuntimeSessionStatus {

    /**
     * 排队中 — 会话已创建但尚未开始执行。
     */
    PENDING("pending", "排队中"),

    /**
     * 运行中 — Agent 正在执行推理或工具调用。
     */
    RUNNING("running", "运行中"),

    /**
     * 等待中 — Agent 暂停等待人工审批或外部事件。
     */
    WAITING("waiting", "等待中"),

    /**
     * 已完成 — Agent 执行成功结束。
     */
    COMPLETED("completed", "已完成"),

    /**
     * 失败 — Agent 执行过程中发生错误。
     */
    FAILED("failed", "失败"),

    /**
     * 已取消 — 用户或管理员主动取消了执行。
     */
    CANCELLED("cancelled", "已取消"),

    /**
     * 已超时 — Agent 执行超过最大允许时长。
     */
    TIMEOUT("timeout", "已超时");

    private final String code;
    private final String desc;

    RuntimeSessionStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据 code 值查找对应的状态枚举。
     *
     * @param code 状态码字符串
     * @return 对应的枚举值，未找到返回 null
     */
    public static RuntimeSessionStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RuntimeSessionStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
