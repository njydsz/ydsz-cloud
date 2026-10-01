package com.njydsz.agent.domain.teamrun;

import java.util.List;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * Team Run 成员状态枚举。
 *
 * <p>定义单个 Agent 在 Team Run 中的执行状态。</p>
 *
 * @author ydsz-agent
 * @since 26.10.01
 */
public enum TeamRunMemberStatus implements BaseStatusEnum<TeamRunMemberStatus> {

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
     * <p>已完成 / 失败 / 已跳过 / 已取消 均不可再迁移。
     *
     * @return 是否为终态
     */
    @Override
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED || this == CANCELLED;
    }

    /**
     * 校验 TeamRun 成员状态流转合法性。
     *
     * <pre>
     * PENDING → RUNNING | SKIPPED | CANCELLED
     * RUNNING → COMPLETED | FAILED | CANCELLED
     * 终态（COMPLETED/FAILED/SKIPPED/CANCELLED）→ 仅自身
     * </pre>
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态流转到目标状态
     */
    @Override
    public boolean canTransitTo(TeamRunMemberStatus target) {
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
            case PENDING -> target == RUNNING || target == SKIPPED || target == CANCELLED;
            case RUNNING -> target == COMPLETED || target == FAILED || target == CANCELLED;
            default -> false;
        };
    }

    /**
     * 返回所有枚举值。
     *
     * @return 全量状态列表
     */
    @Override
    public List<TeamRunMemberStatus> allStates() {
        return List.of(values());
    }
}
