package com.njydsz.pmis.common.dag;

/**
 * 统一 DAG 节点执行状态枚举（P0-1 架构优化）。
 *
 * <p>合并 cronjob 和 agent 两个模块的节点状态枚举，消除重复定义。
 *
 * <p>状态流转：
 * <pre>
 * PENDING → RUNNING → SUCCESS
 *                  → FAILED → RETRYING → PENDING（节点级重试）
 *         → SKIPPED（前置失败或条件不满足）
 *         → WAITING_FOR_APPROVAL → SUCCESS / APPROVAL_REJECTED
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-1)
 */
public enum DagNodeStatus {

    /** 待执行（初始态） */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 执行成功（终态） */
    SUCCESS,

    /** 执行失败（终态或待重试） */
    FAILED,

    /** 跳过（前置失败或条件不满足，终态） */
    SKIPPED,

    /** 重试中（FAILED → RETRYING → PENDING） */
    RETRYING,

    /** 等待审批（APPROVAL 节点执行后进入此状态） */
    WAITING_FOR_APPROVAL,

    /** 审批拒绝（终态） */
    APPROVAL_REJECTED;

    /**
     * 判断是否为终态（不可再流转）。
     *
     * @return true 表示该状态不再变化
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == SKIPPED || this == APPROVAL_REJECTED;
    }

    /**
     * 判断是否为成功终态。
     *
     * @return true 表示节点成功完成
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * 判断是否为失败终态（含跳过和审批拒绝）。
     *
     * @return true 表示节点以失败/跳过/拒绝结束
     */
    public boolean isFailed() {
        return this == FAILED || this == SKIPPED || this == APPROVAL_REJECTED;
    }

    /**
     * 校验状态流转是否合法。
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态流转到 target
     */
    public boolean canTransitTo(DagNodeStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == RUNNING || target == SKIPPED;
            case RUNNING -> target == SUCCESS || target == FAILED || target == SKIPPED
                    || target == WAITING_FOR_APPROVAL;
            case FAILED -> target == RUNNING || target == RETRYING;
            case WAITING_FOR_APPROVAL -> target == SUCCESS || target == APPROVAL_REJECTED;
            case RETRYING -> target == PENDING;
            case SUCCESS, SKIPPED, APPROVAL_REJECTED -> false;
        };
    }

    /**
     * 安全解析状态字符串，无效值返回 null。
     *
     * @param value 状态字符串
     * @return 对应枚举值；无效返回 null
     */
    public static DagNodeStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DagNodeStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
