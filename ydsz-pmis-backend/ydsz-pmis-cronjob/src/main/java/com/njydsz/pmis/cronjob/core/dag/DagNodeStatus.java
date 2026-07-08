package com.njydsz.pmis.cronjob.core.dag;

/**
 * DAG 节点实例状态枚举（P2 DAG 增强）。
 *
 * <p>状态流转：
 * <pre>
 * PENDING → RUNNING → SUCCESS
 *                  → FAILED → RETRYING → PENDING（节点级重试）
 *         → SKIPPED（前置失败且 FAIL_FAST 时跳过）
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DagNodeStatus {

    /** 待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 成功 */
    SUCCESS,

    /** 失败 */
    FAILED,

    /** 跳过（前置失败且 FAIL_FAST） */
    SKIPPED,

    /** 重试中（FAILED → RETRYING → PENDING） */
    RETRYING,

    /** P1-6: 等待审批（APPROVAL 节点执行后进入此状态，审批通过后变为 SUCCESS） */
    WAITING_FOR_APPROVAL,

    /** P1-6: 审批拒绝（审批人拒绝后进入此终态，DAG 可按策略中止或继续） */
    APPROVAL_REJECTED;

    /**
     * 判断是否为终态。
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == APPROVAL_REJECTED;
    }

    /**
     * 判断是否为成功终态。
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * 判断是否为失败终态（含跳过和审批拒绝）。
     */
    public boolean isFailed() {
        return this == FAILED || this == SKIPPED || this == APPROVAL_REJECTED;
    }

    /**
     * 安全解析状态字符串，无效值返回 null。
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
