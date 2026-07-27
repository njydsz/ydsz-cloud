package com.njydsz.common.core.dag;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DAG 节点实例状态枚举。
 *
 * <p>节点实例的生命周期状态：
 * <pre>
 * PENDING → RUNNING → SUCCESS / FAILED / SKIPPED
 *                      ↓
 *                   RETRYING → RUNNING → ...
 *
 * 审批场景扩展：
 * RUNNING → WAITING_FOR_APPROVAL → SUCCESS / APPROVAL_REJECTED
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DagNodeStatus {

    /** 待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 执行成功 */
    SUCCESS,

    /** 执行失败 */
    FAILED,

    /** 已跳过（前置节点失败或条件不满足） */
    SKIPPED,

    /** 重试中（失败后重新排队） */
    RETRYING,

    /** 等待审批（审批流节点） */
    WAITING_FOR_APPROVAL,

    /** 审批被拒绝 */
    APPROVAL_REJECTED;

    private static final Map<String, DagNodeStatus> CACHE = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, Function.identity()));

    /**
     * 根据名称解析状态。
     *
     * @param name 状态名称
     * @return 状态枚举，未找到时返回 null
     */
    public static DagNodeStatus parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return CACHE.get(name);
    }

    /**
     * 是否为终态。
     *
     * @return true 表示终态（SUCCESS / FAILED / SKIPPED / APPROVAL_REJECTED）
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == APPROVAL_REJECTED;
    }

    /**
     * 是否为活跃态（可被调度执行）。
     *
     * @return true 表示活跃态（PENDING / RETRYING）
     */
    public boolean isActive() {
        return this == PENDING || this == RETRYING;
    }
}
