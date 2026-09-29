package com.njydsz.agent.domain.teamrun;

import java.util.List;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * Team Run 状态枚举。
 *
 * <p>定义多 Agent 协作执行的生命周期状态。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public enum TeamRunStatus implements BaseStatusEnum<TeamRunStatus> {

    /** 已创建，等待启动 */
    CREATED("CREATED", "agent.teamrun.status.created"),

    /** 正在执行中 */
    RUNNING("RUNNING", "agent.teamrun.status.running"),

    /** 等待人工审批 */
    WAITING_APPROVAL("WAITING_APPROVAL", "agent.teamrun.status.waiting_approval"),

    /** 所有 Agent 执行完成 */
    COMPLETED("COMPLETED", "agent.teamrun.status.completed"),

    /** 部分或全部 Agent 执行失败 */
    FAILED("FAILED", "agent.teamrun.status.failed"),

    /** 已被用户取消 */
    CANCELLED("CANCELLED", "agent.teamrun.status.cancelled"),

    /** 执行超时被终止 */
    TIMEOUT("TIMEOUT", "agent.teamrun.status.timeout");

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
     * <p>已完成 / 失败 / 已取消 / 超时的 Team Run 不可再迁移。
     *
     * @return 是否为终态
     */
    @Override
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    /**
     * 校验 TeamRun 状态流转合法性。
     *
     * <pre>
     * CREATED          → RUNNING | CANCELLED | TIMEOUT
     * RUNNING          → WAITING_APPROVAL | COMPLETED | FAILED | CANCELLED | TIMEOUT
     * WAITING_APPROVAL → RUNNING | COMPLETED | FAILED | CANCELLED | TIMEOUT
     * 终态（COMPLETED/FAILED/CANCELLED/TIMEOUT）→ 仅自身
     * </pre>
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态流转到目标状态
     */
    @Override
    public boolean canTransitTo(TeamRunStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        if (this.isTerminal()) {
            return false;
        }
        return switch (this) {
            case CREATED -> target == RUNNING || target == CANCELLED || target == TIMEOUT;
            case RUNNING, WAITING_APPROVAL -> target != CREATED;
            default -> false;
        };
    }

    /**
     * 返回所有枚举值。
     *
     * @return 全量状态列表
     */
    @Override
    public List<TeamRunStatus> allStates() {
        return List.of(values());
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
